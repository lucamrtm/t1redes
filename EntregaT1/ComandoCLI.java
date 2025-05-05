import java.net.*;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

class ComandoCLI extends Thread {
    private ConcurrentHashMap<String, Dispositivo> dispositivos;
    private DatagramSocket socket;
    private DatagramSocket socketFile; // NOVO

    public ComandoCLI(ConcurrentHashMap<String, Dispositivo> dispositivos, DatagramSocket socket,
            DatagramSocket socketFile) {
        this.dispositivos = dispositivos;
        this.socket = socket;
        this.socketFile = socketFile; // guardamos o socket novo
    }

    public void run() {
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("> ");
            String linha = scanner.nextLine();
            String[] partes = linha.trim().split(" ", 3);
            if (partes[0].equalsIgnoreCase("devices")) {
                System.out.println("Dispositivos ativos:");
                dispositivos.forEach((nome, disp) -> {
                    long diff = (System.currentTimeMillis() - disp.ultimoHeartbeat) / 1000;
                    System.out.println(nome + " - IP: " + disp.ip + " (último heartbeat há " + diff + "s)");
                });
            } else if (partes[0].equalsIgnoreCase("talk") && partes.length >= 3) {
                String destino = partes[1];
                String mensagem = partes[2];
                Dispositivo disp = dispositivos.get(destino);
                if (disp != null) {
                    try {
                        String msg = "TALK " + mensagem;
                        byte[] buffer = msg.getBytes();
                        DatagramPacket pacote = new DatagramPacket(buffer, buffer.length,
                                InetAddress.getByName(disp.ip), 5000);
                        socket.send(pacote);
                        System.out.println("Mensagem enviada para " + destino);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    System.out.println("Dispositivo não encontrado.");
                }
            } else if (linha.startsWith("sendfile ")) {
                if (partes.length < 3) {
                    System.out.println("Uso: sendfile <nome> <nome-arquivo>");
                    continue;
                }
                String nomeDestino = partes[1];
                String nomeArquivo = partes[2];

                Dispositivo alvo = dispositivos.get(nomeDestino);
                if (alvo == null) {
                    System.out.println("Dispositivo não encontrado.");
                } else {
                    new FileSender(socketFile, alvo, nomeArquivo).start(); // USA O SOCKET NOVO
                }
            } else {
                System.out.println("Comando desconhecido. Use: devices ou talk <nome> <mensagem>");
            }
        }
    }
}
