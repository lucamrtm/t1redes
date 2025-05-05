import java.net.*;

public class HeartbeatSender extends Thread {
    private DatagramSocket socket;
    private String nome;

    public HeartbeatSender(DatagramSocket socket, String nome) {
        this.socket = socket;
        this.nome = nome;
    }

    public void run() {
        try {
            InetAddress broadcastAddr = InetAddress.getByName("192.168.0.255");

            while (true) {
                String mensagem = "HEARTBEAT " + nome;
                byte[] buffer = mensagem.getBytes();
                DatagramPacket pacote = new DatagramPacket(buffer, buffer.length, broadcastAddr, 5000);
                socket.send(pacote);

                Thread.sleep(5000);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
