import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

//Cliente 1 e 3 não recebem tarefas, de dois em dois recebem a tarefa que devia ser só para o cliente com id 2

public class Client {
    private static final int SERVER_PORT = 12345;
    private Integer clientId = null;
    private DatagramSocket socket;
    private InetAddress serverAddress;
    private AtomicInteger sequenceNumber = new AtomicInteger(1);

    public Client() throws IOException {
        this.socket = new DatagramSocket();
        this.serverAddress = InetAddress.getByName("localhost");
    }

    public void register() {
        try {
            int newSequenceNumber = sequenceNumber.getAndIncrement();
            String registrationMessage = createDatagramReg(1, newSequenceNumber);
            DatagramPacket registrationPacket = new DatagramPacket(
                    registrationMessage.getBytes(), registrationMessage.length(), serverAddress, SERVER_PORT
            );
            socket.send(registrationPacket);

            byte[] buffer = new byte[1024];
            DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);
            socket.receive(responsePacket);

            String responseMessage = new String(responsePacket.getData(), 0, responsePacket.getLength());
            String[] parts = responseMessage.split("\\|");

            int type = Integer.parseInt(parts[0]);
            int sequenceNumber = Integer.parseInt(parts[1]);
            int receivedClientId = Integer.parseInt(parts[2]);

            if (3 == type) {
                clientId = receivedClientId;
                System.out.println("Registo confirmado. Foi-me atribuído o id: " + clientId);
            } else {
                System.out.println("Falha ao registar no servidor.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void requestTask() {
        try {
            int newSequenceNumber = sequenceNumber.getAndIncrement();
            String requestMessage = createDatagramReq(2, newSequenceNumber, clientId);
            DatagramPacket taskPacket = new DatagramPacket(
                    requestMessage.getBytes(), requestMessage.length(), serverAddress, SERVER_PORT
            );
            socket.send(taskPacket);

            byte[] buffer = new byte[1024];
            DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);
            socket.receive(responsePacket);

            String responseMessage = new String(responsePacket.getData(), 0, responsePacket.getLength());
            String[] parts = responseMessage.split("\\|");

            int tipo = Integer.parseInt(parts[0]);
            int taskClientId = Integer.parseInt(parts[2]);
            String comando = parts[4];
            
            String[] comandoPartes = comando.split(",");

            int frequencia = Integer.parseInt(parts[5]);
            
            if (4 == tipo && taskClientId == clientId) {
                System.out.println("Recebi a tarefa: " + comando + " com frequência de " + frequencia + " segundos.");

                // Envia o ACK ao servidor
                int ackSequenceNumber = sequenceNumber.getAndIncrement();
                String ackMessage = createAckDatagram(3, ackSequenceNumber, clientId);
                DatagramPacket ackPacket = new DatagramPacket(
                        ackMessage.getBytes(), ackMessage.length(), serverAddress, SERVER_PORT
                );
                socket.send(ackPacket);
                System.out.println("ACK enviado ao servidor.");

                // Executa a tarefa recebida
                executeTask(comando, frequencia);
            } else {
                System.out.println("Tarefa recebida não corresponde ao meu ID.");
            }
        
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void executeTask(String command, int frequency) {
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

        executor.scheduleAtFixedRate(() -> {
            try {
                // Simulação da execução do comando
                String result = "Executando: " + command + " - Resultado: OK";

                // Envia o resultado ao servidor
                sendTaskResult(result);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, frequency, TimeUnit.SECONDS);
    }

    private void sendTaskResult(String result) {
        try {
            int newSequenceNumber = sequenceNumber.getAndIncrement();
            String resultMessage = createDatagramTask(5, newSequenceNumber, clientId, result);
            DatagramPacket resultPacket = new DatagramPacket(
                    resultMessage.getBytes(), resultMessage.length(), serverAddress, SERVER_PORT
            );
            socket.send(resultPacket);
            System.out.println("Resultado enviado ao servidor: " + result);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String createDatagramTask(int type, int seqNum, int clientId, String result) {
        return type + "|" + seqNum + "|" + clientId + "|" + result;
    }

    private String createAckDatagram(int type, int seqNum, int clientId) {
        return type + "|" + seqNum + "|" + clientId;
    }

    private String createDatagramReg(int type, int sequenceNumber) {
        return type + "|" + sequenceNumber;
    }

    private String createDatagramReq(int type, int sequenceNumber, Integer clientId) {
        return type + "|" + sequenceNumber + "|"+ clientId;
    }

    public static void main(String[] args) {
        try {
            Client client = new Client();
            client.register();
            client.requestTask();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
