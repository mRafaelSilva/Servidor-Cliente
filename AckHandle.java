import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AckHandle {
    
    private final List<PendingAck> pendingAcks = new ArrayList<>();
    private final DatagramSocket socket;
    private DatagramUtils utils;
    private final ScheduledExecutorService retransmissionExecutor = Executors.newScheduledThreadPool(1); // Thread para retransmissão
    private static final int RETRANSMISSION_INTERVAL_MS = 10000; // Intervalo de retransmissão em milissegundos

    // aqui tenho de ver se vai ser preciso criar um novo socket
    public AckHandle(DatagramSocket socket,DatagramUtils utils) {
        this.socket = socket;
        this.utils = utils;

        startRetransmissionTask();
    }


    private class PendingAck {
        int nSequencia;
        int clientId;
        InetAddress endereco;
        int porta;
        DatagramPacket packet; // Armazena o pacote enviado
    
        public PendingAck(int nSequencia, int clientId, InetAddress endereco, int porta, DatagramPacket packet) {
            this.nSequencia = nSequencia;
            this.clientId = clientId;
            this.endereco = endereco;
            this.porta = porta;
            this.packet = packet;
        }
    }

    public void sendAck(int nSequencia, int clientId, InetAddress endereco, int porta, DatagramSocket socket) {
        try {

            String mensagem = utils.criaDatagramaAck(3,nSequencia, clientId);

            DatagramPacket pacote = new DatagramPacket(mensagem.getBytes(), mensagem.length(), endereco, porta);

            socket.send(pacote);
            System.out.println("Mensagem enviada para " + endereco + ":" + porta + " com seq=" + nSequencia + " e id " + clientId);
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


        public void sendEGuardaAck(int nSequencia, int clientId, InetAddress endereco, int porta, DatagramSocket socket) {
            try {
                // Criação do datagrama
                String message = utils.criaDatagramaAck(3,nSequencia, clientId);
    
                DatagramPacket packet = new DatagramPacket(message.getBytes(), message.length(), endereco, porta);
        
                // Adicionar à lista de pendências
                synchronized (pendingAcks) {
                    pendingAcks.add(new PendingAck(nSequencia, clientId, endereco, porta, packet));
                }
        
                // Enviar o pacote
                socket.send(packet);
                System.out.println("Mensagem enviada para " + endereco + ":" + porta + " com seq=" + nSequencia);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        //FAZER PROCESSACK QUE NÃO USA CLIENTID

        public void criaAckPendente(int nSequencia, int clientId, InetAddress endereco, int porta, DatagramPacket tarefa) {
        
            synchronized(pendingAcks) {
                pendingAcks.add(new PendingAck(nSequencia, clientId, endereco, porta, tarefa));        
            }

            System.out.println("Estou à espera do Ack " + nSequencia + " do id " + clientId);

        }
        
        public void processAckReg(int nSequencia) {
            synchronized (pendingAcks) {
                pendingAcks.removeIf(pendingAck -> 
                    pendingAck.nSequencia == nSequencia
                );
            }
            System.out.println("ACK processado do reg");
        }


    public void processAck(int nSequencia, int clientId) {
        synchronized (pendingAcks) {
            pendingAcks.removeIf(pendingAck -> 
                pendingAck.nSequencia == nSequencia &&
                pendingAck.clientId == clientId
            );
        }
        System.out.println("ACK processado para seq=" + nSequencia + " do cliente " + clientId);
    }

    public void startRetransmissionTask() {
        retransmissionExecutor.scheduleAtFixedRate(() -> {
            synchronized (pendingAcks) {
                Iterator<PendingAck> iterator = pendingAcks.iterator();
                while (iterator.hasNext()) {
                    PendingAck pendingAck = iterator.next();
                    try {
                        // Retransmissão do pacote
                        socket.send(pendingAck.packet);
                        System.out.println("Retransmitindo pacote para " + pendingAck.endereco + ":" + pendingAck.porta + 
                                           " com seq=" + pendingAck.nSequencia);
                    } catch (IOException e) {
                        System.err.println("Erro ao retransmitir pacote: " + e.getMessage());
                    }
                }
            }
        }, RETRANSMISSION_INTERVAL_MS, RETRANSMISSION_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Encerra o executor de retransmissão.
     */
    public void stopRetransmissionTask() {
        retransmissionExecutor.shutdown();
        try {
            if (!retransmissionExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                retransmissionExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            retransmissionExecutor.shutdownNow();
        }
    }

}
