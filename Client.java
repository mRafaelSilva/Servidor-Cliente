import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


// Implementar a lógica de, se não receber nada no Registo, no Pedido, ou no Resultado, enviar novamente
// Temos de criar também uma lista que guarda os Resultados enviados e o Ack que pretende receber como resposta


public class Client {
    private static final int SERVER_PORT = 12345;
    private Integer clientId = null;
    private DatagramSocket socket;
    private InetAddress serverAddress;
    private AtomicInteger sequenceNumber = new AtomicInteger(1);
    private DatagramUtils utils;
    private AckHandle ackHandle;

    public Client() throws IOException {
        this.socket = new DatagramSocket();
        this.serverAddress = InetAddress.getByName("localhost");
        this.utils = new DatagramUtils();
        ackHandle = new AckHandle(socket, utils);
    }

    public void register() {
        try {
            int newSequenceNumber = sequenceNumber.getAndIncrement();

            System.out.println("SEQUENCE NUMBER INICIAL" + newSequenceNumber);

            String registrationMessage = utils.criaDatagramaRegisto(1, newSequenceNumber); 

            DatagramPacket registrationPacket = new DatagramPacket(
                    registrationMessage.getBytes(), registrationMessage.length(), serverAddress, SERVER_PORT
            );
            socket.send(registrationPacket);
            System.out.println("Pedido de Registo enviado: Tipo=1, SeqNum=" + newSequenceNumber + ", ClientId=" + clientId + " Porta " + SERVER_PORT + " Endereço " + serverAddress);


            
            byte[] buffer = new byte[1024];
            DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);
            synchronized (socket) {
            socket.receive(responsePacket);
            }
            String responseMessage = new String(responsePacket.getData(), 0, responsePacket.getLength());
            String[] parts = responseMessage.split("\\|");

            int type = Integer.parseInt(parts[0]);
            int sequenceNumber = Integer.parseInt(parts[1]);
            int receivedClientId = Integer.parseInt(parts[2]);

            if (3 == type && sequenceNumber == newSequenceNumber) {
                clientId = receivedClientId;
                System.out.println("Registo confirmado. Foi-me atribuído o id: " + clientId);

                ackHandle.sendAck(newSequenceNumber, clientId, serverAddress, SERVER_PORT, socket);

            } else {
                System.out.println("Falha ao registar no servidor.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void requestTask() {
        try {
            int newSequenceNumber = sequenceNumber.getAndIncrement(); // Sequência 2

            //Aqui ele pede uma tarefa
            String requestMessage = utils.criaDatagramaPedirTarefaString(2, newSequenceNumber, clientId);
            DatagramPacket taskPacket = new DatagramPacket(
                    requestMessage.getBytes(), requestMessage.length(), serverAddress, SERVER_PORT
            );
            socket.send(taskPacket);

            // Aqui ele recebe a tarefa
            byte[] buffer = new byte[1024];
            DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);
            socket.receive(responsePacket);
            
            String responseMessage = new String(responsePacket.getData(), 0, responsePacket.getLength());
            String[] parts = responseMessage.split("\\|");

            int tipo = Integer.parseInt(parts[0]);
            int taskClientId = Integer.parseInt(parts[2]);
            String comando = parts[4];
            
            if (4 == tipo && taskClientId == clientId) {

                String[] comandoPartes = comando.split(",");
                String taskId = null, codigo = null;
                int frequencia = 0;
    
                for (String part : comandoPartes) {
                    if (part.startsWith("task_id=")) {
                        taskId = part.split("=")[1];
                    } else if (part.startsWith("command=")) {
                        codigo = part.split("=")[1];
                    } else if (part.startsWith("frequency=")) {
                        frequencia = Integer.parseInt(part.split("=")[1]);
                    }
                }

                if (taskId != null && comando != null && frequencia > 0) {  // Ele diz que recebeu a tarefa com sequencia 2
                    System.out.println("Recebi a tarefa: " + codigo + " com frequência de " + frequencia + " segundos.");

                    ackHandle.sendAck(newSequenceNumber, clientId, serverAddress, SERVER_PORT, socket);  // sequência 2
                    
                    System.out.println("ACK enviado ao servidor.");

                    // Executa a tarefa recebida    
                    executeTask(codigo, frequencia);
                } else {
                    System.out.println("Erro ao interpretar os campos da tarefa.");
                }
            } else {
                System.out.println("Tarefa recebida não corresponde ao meu ID.");
            }
        
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void executeTask(String comando, int frequency) {
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);

        executor.scheduleAtFixedRate(() -> {
            try {
                String result = "Estou a executar: " + comando;

                // Envia o resultado ao servidor
                sendTaskResult(result); 
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 0, frequency, TimeUnit.SECONDS);
    }

    private void sendTaskResult(String result) {
        try {
            int newSequenceNumber = sequenceNumber.getAndIncrement();  // sequencia 3
            String resultMessage = utils.criaDatagramaResultado(5, newSequenceNumber, clientId, result);

            DatagramPacket resultPacket = new DatagramPacket(
                    resultMessage.getBytes(), resultMessage.length(), serverAddress, SERVER_PORT
            );
            socket.send(resultPacket);

            
            // Implementar aqui a lógica também de guardar numa lista
            // As listas vão ter de ser iniciadas: uma no Client e uma no Server?

            // Substituir este por uma apropriada para o server
            //ackHandle.criaAckPendente(newSequenceNumber, clientId, serverAddress, SERVER_PORT, resultPacket);

            
            System.out.println("Resultado enviado ao servidor: " + result);
        } catch (IOException e) {
            e.printStackTrace();
        }
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