import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public class LimpezaDispositivos extends Thread {
    private ConcurrentHashMap<String, Dispositivo> dispositivos;

    public LimpezaDispositivos(ConcurrentHashMap<String, Dispositivo> dispositivos) {
        this.dispositivos = dispositivos;
    }

    public void run() {
        while (true) {
            long agora = System.currentTimeMillis();
            ArrayList<String> paraRemover = new ArrayList<>();

            // Descobre quem precisa ser removido
            dispositivos.forEach((nome, disp) -> {
                long diff = (agora - disp.ultimoHeartbeat) / 1000; // em segundos
                if (diff > 10) {
                    paraRemover.add(nome);
                    System.out.printf("[INFO] Dispositivo %s está inativo há %ds, agendado para remoção.%n", nome,
                            diff);
                }
            });

            // Remove os inativos fora do loop principal
            for (String nome : paraRemover) {
                dispositivos.remove(nome);
                System.out.printf("[INFO] Dispositivo %s removido com sucesso.%n", nome);
            }

            try {
                Thread.sleep(2000); // Espera 2s antes de repetir a limpeza
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
