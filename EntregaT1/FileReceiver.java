import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.TreeMap;
import java.security.MessageDigest;

public class FileReceiver {
    private String id;
    private String nomeArquivo;
    private long tamanho;
    private TreeMap<Integer, byte[]> chunks = new TreeMap<>();
    private long bytesRecebidos = 0;

    public FileReceiver(String id, String nomeArquivo, long tamanho) {
        this.id = id;
        this.nomeArquivo = nomeArquivo;
        this.tamanho = tamanho;
    }

    public String getNomeArquivo() {
        return nomeArquivo;
    }

    public synchronized boolean adicionarChunk(int seq, String dadosBase64) {
        if (chunks.containsKey(seq)) {
            return false; // Chunk duplicado
        }
        byte[] dados = Base64.getDecoder().decode(dadosBase64);
        chunks.put(seq, dados);
        bytesRecebidos += dados.length;
        System.out.println("Chunk " + seq + " recebido (" + dados.length + " bytes)");
        return true;
    }

    public synchronized boolean finalizarArquivo(String hashEsperado) {
        try (FileOutputStream fos = new FileOutputStream(nomeArquivo)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Map.Entry<Integer, byte[]> entry : chunks.entrySet()) {
                byte[] dados = entry.getValue();
                fos.write(dados);
                digest.update(dados);
            }
            String hashCalculado = bytesParaHex(digest.digest());
            System.out.println("Hash esperado: " + hashEsperado);
            System.out.println("Hash calculado: " + hashCalculado);
            return hashCalculado.equalsIgnoreCase(hashEsperado);
        } catch (IOException | java.security.NoSuchAlgorithmException e) {
            e.printStackTrace();
            return false;
        }
    }

    private String bytesParaHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
