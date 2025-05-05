public class Dispositivo {
    String nome;
    String ip;
    long ultimoHeartbeat;

    public Dispositivo(String nome, String ip) {
        this.nome = nome;
        this.ip = ip;
        this.ultimoHeartbeat = System.currentTimeMillis();
    }
}
