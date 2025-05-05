import java.net.*;
import java.util.concurrent.ConcurrentHashMap;

public class DeviceUDP {
    public static void main(String[] args) throws Exception {
        String meuNome = "LucaDevice";
        int porta = 5000;
        if (args.length > 0) {
            meuNome = args[0];
        }

        DatagramSocket socketControl = new DatagramSocket(porta);
        socketControl.setBroadcast(true);

        DatagramSocket socketFile = new DatagramSocket(); // NOVO SOCKET PARA ARQUIVOS

        ConcurrentHashMap<String, Dispositivo> dispositivosAtivos = new ConcurrentHashMap<>();
        ConcurrentHashMap<String, FileReceiver> arquivosRecebidos = new ConcurrentHashMap<>();

        // Inicia threads
        new HeartbeatSender(socketControl, meuNome).start();
        new LimpezaDispositivos(dispositivosAtivos).start();
        new ComandoCLI(dispositivosAtivos, socketControl, socketFile).start();

        byte[] bufferReceber = new byte[8192];

        System.out.println("Device UDP iniciado. Nome: " + meuNome);
        System.out.println("Porta de controle: " + socketControl.getLocalPort());
        System.out.println("Porta de arquivos: " + socketFile.getLocalPort());

        while (true) {
            DatagramPacket pacoteRecebido = new DatagramPacket(bufferReceber, bufferReceber.length);
            socketControl.receive(pacoteRecebido);

            String mensagem = new String(pacoteRecebido.getData(), 0, pacoteRecebido.getLength());
            String ipRemetente = pacoteRecebido.getAddress().getHostAddress();

            if (mensagem.startsWith("HEARTBEAT")) {
                String nomeDispositivo = mensagem.split(" ")[1];
                dispositivosAtivos.put(nomeDispositivo, new Dispositivo(nomeDispositivo, ipRemetente));

            } else if (mensagem.startsWith("TALK")) {
                System.out.println("Recebido TALK: " + mensagem);
                enviarAck(socketControl, pacoteRecebido, "TALK");

            } else if (mensagem.startsWith("FILE")) {
                System.out.println("Recebido FILE: " + mensagem);
                String[] partes = mensagem.split(" ", 4);
                String id = partes[1];
                String nomeArquivo = partes[2];
                long tamanho = Long.parseLong(partes[3]);

                FileReceiver receiver = new FileReceiver(id, nomeArquivo, tamanho);
                arquivosRecebidos.put(id, receiver);
                enviarAck(socketControl, pacoteRecebido, id);

            } else if (mensagem.startsWith("CHUNK")) {
                String[] partes = mensagem.split(" ", 4);
                String id = partes[1];
                int seq = Integer.parseInt(partes[2]);
                String dados = partes[3];

                FileReceiver receiver = arquivosRecebidos.get(id);
                if (receiver != null) {
                    boolean novo = receiver.adicionarChunk(seq, dados);
                    if (novo) {
                        enviarAck(socketControl, pacoteRecebido, id);
                    } else {
                        System.out.println("Chunk duplicado descartado (id=" + id + ", seq=" + seq + ")");
                    }
                } else {
                    System.out.println("Recebido CHUNK para arquivo desconhecido: " + id);
                }

            } else if (mensagem.startsWith("END")) {
                String[] partes = mensagem.split(" ", 3);
                String id = partes[1];
                String hashRecebido = partes[2];

                FileReceiver receiver = arquivosRecebidos.get(id);
                if (receiver != null) {
                    boolean ok = receiver.finalizarArquivo(hashRecebido);
                    String resposta = ok ? "ACK " + id : "NACK " + id + " hash inválido";
                    byte[] bufferEnviar = resposta.getBytes();
                    DatagramPacket pacoteAck = new DatagramPacket(
                            bufferEnviar,
                            bufferEnviar.length,
                            pacoteRecebido.getAddress(),
                            pacoteRecebido.getPort());
                    socketControl.send(pacoteAck);
                    if (ok) {
                        System.out.println("Arquivo " + receiver.getNomeArquivo() + " recebido com sucesso!");
                    } else {
                        System.out.println("Arquivo " + receiver.getNomeArquivo() + " corrompido (hash inválido)!");
                    }
                    arquivosRecebidos.remove(id);
                } else {
                    System.out.println("Recebido END para arquivo desconhecido: " + id);
                }

            } else if (mensagem.startsWith("ACK") || mensagem.startsWith("NACK")) {
                System.out.println("Recebido: " + mensagem);

            } else {
                System.out.println("Mensagem desconhecida: " + mensagem);
            }
        }
    }

    private static void enviarAck(DatagramSocket socket, DatagramPacket pacoteRecebido, String id) throws Exception {
        String ack = "ACK " + id;
        byte[] bufferEnviar = ack.getBytes();
        DatagramPacket pacoteAck = new DatagramPacket(
                bufferEnviar,
                bufferEnviar.length,
                pacoteRecebido.getAddress(),
                pacoteRecebido.getPort());
        socket.send(pacoteAck);
    }
}
