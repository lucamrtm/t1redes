import java.io.*;
import java.net.*;
import java.util.Base64;
import java.util.UUID;
import java.security.MessageDigest;
import java.util.concurrent.ConcurrentHashMap;

public class FileSender extends Thread {
    private DatagramSocket socket;
    private Dispositivo destino;
    private String nomeArquivo;
    private static final int TAMANHO_BLOCO = 1024; // 1 KB por bloco
    private ConcurrentHashMap<String, Boolean> ackTracker = new ConcurrentHashMap<>();

    public FileSender(DatagramSocket socket, Dispositivo destino, String nomeArquivo) {
        this.socket = socket;
        this.destino = destino;
        this.nomeArquivo = nomeArquivo;
    }

    private boolean enviarEConfirmar(String mensagem, String id) throws Exception {
        byte[] buffer = mensagem.getBytes();
        DatagramPacket pacote = new DatagramPacket(
                buffer,
                buffer.length,
                InetAddress.getByName(destino.ip),
                5000);

        boolean ackRecebido = false;
        int tentativas = 0;
        while (!ackRecebido && tentativas < 5) {
            System.out.println("Enviando: " + mensagem);
            socket.send(pacote);

            try {
                socket.setSoTimeout(2000); // Espera 2 segundos
                byte[] bufferAck = new byte[1024];
                DatagramPacket ackPacket = new DatagramPacket(bufferAck, bufferAck.length);
                while (true) {
                    socket.receive(ackPacket);
                    String resposta = new String(ackPacket.getData(), 0, ackPacket.getLength());

                    // Só quebra o loop se for o ACK esperado
                    if (resposta.startsWith("ACK " + id)) {
                        System.out.println("ACK recebido para ID: " + id);
                        ackRecebido = true;
                        return true;
                    } else if (resposta.startsWith("NACK " + id)) {
                        System.out.println("NACK recebido para ID: " + id + " -> Falhou");
                        return false;
                    } else {
                        // ✅ Ignore outros pacotes (HEARTBEAT, TALK, etc.)
                        System.out.println("Ignorando mensagem não relacionada: " + resposta);
                    }
                }

            } catch (SocketTimeoutException e) {
                System.out.println("Timeout esperando ACK para ID: " + id);
                tentativas++;
            }
        }
        return false;
    }

    @Override
    public void run() {
        File arquivo = new File(nomeArquivo);
        if (!arquivo.exists()) {
            System.out.println("Arquivo não encontrado: " + nomeArquivo);
            return;
        }

        String id = UUID.randomUUID().toString();
        long tamanho = arquivo.length();

        try {
            // Enviar FILE
            String msgFile = "FILE " + id + " " + arquivo.getName() + " " + tamanho;
            if (!enviarEConfirmar(msgFile, id)) {
                System.out.println("Falha ao enviar FILE header após várias tentativas.");
                return;
            }

            // Enviar CHUNKs
            FileInputStream fis = new FileInputStream(arquivo);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] bufferBloco = new byte[TAMANHO_BLOCO];
            int bytesLidos;
            int seq = 0;

            while ((bytesLidos = fis.read(bufferBloco)) != -1) {
                byte[] dadosParaEnviar = new byte[bytesLidos];
                System.arraycopy(bufferBloco, 0, dadosParaEnviar, 0, bytesLidos);

                // Atualiza o hash
                digest.update(dadosParaEnviar);

                // Codifica em base64
                String dadosBase64 = Base64.getEncoder().encodeToString(dadosParaEnviar);

                String msgChunk = "CHUNK " + id + " " + seq + " " + dadosBase64;
                if (!enviarEConfirmar(msgChunk, id)) {
                    System.out.println("Falha ao enviar CHUNK " + seq);
                    fis.close();
                    return;
                }

                seq++;
            }
            fis.close();

            // Enviar END
            String hashFinal = bytesToHex(digest.digest());
            String msgEnd = "END " + id + " " + hashFinal;
            if (!enviarEConfirmar(msgEnd, id)) {
                System.out.println("Falha ao enviar END após várias tentativas.");
                return;
            }

            System.out.println("Arquivo enviado com sucesso!");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
