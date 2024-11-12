import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;


//Alterar  o HashMap para o Id do cliente

public class Server {
    private static final int PORT = 12345;
    private DatagramSocket socket;
    private List <Integer> clients = new ArrayList<>(); 
    private AtomicInteger clientIdCounter = new AtomicInteger(1); 

    public Server() throws IOException {
        socket = new DatagramSocket(PORT);
        System.out.println("Servidor iniciado na porta " + PORT);
    }

    public void listen() {
        while (true) {
            try {
                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                new Thread(() -> handleClient(packet)).start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleClient(DatagramPacket packet) {
        try {
            String message = new String(packet.getData(), 0, packet.getLength());
            String[] parts = message.split("\\|");

            int type = Integer.parseInt(parts[0]);
            int sequenceNumber = Integer.parseInt(parts[1]);
            
            InetAddress clientAddress = packet.getAddress();
            int clientPort = packet.getPort();

            if (1==type) {
                int newClientId = clientIdCounter.getAndIncrement();
                clients.add(newClientId);

                System.out.println("Registei o id " + clients.get(newClientId-1));

                System.out.println("Cliente registado. Foi-lhe atribuído o ID: " + newClientId);

                String ackMessage = createDatagramAckReg(3, sequenceNumber, newClientId);
                DatagramPacket ackPacket = new DatagramPacket(
                        ackMessage.getBytes(), ackMessage.length(), clientAddress, clientPort
                );
                socket.send(ackPacket);
            }

            if (2==type) {
        
                if (parts[2] == null || parts[2].isEmpty() ) return;

                int cliente = Integer.parseInt(parts[2]);
                if (!clients.contains(cliente)) {
                    return;
                } else {
                    
                    System.out.println("O cliente com o id " + cliente + " está a pedir uma tarefa");
                    
                    String taskCommand = readTaskFromJSON(cliente);
                    String taskMessage = createTaskDatagram(4, sequenceNumber, cliente, taskCommand);
                
                    DatagramPacket taskPacket = new DatagramPacket(
                        taskMessage.getBytes(), taskMessage.length(), clientAddress, clientPort
                    );
                    socket.send(taskPacket);

                    // Aguardar o ACK (ID 3)
                    byte[] ackBuffer = new byte[1024];
                    DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length);
                    socket.receive(ackPacket);

                    String ackResponse = new String(ackPacket.getData(), 0, ackPacket.getLength());
                    String[] ackParts = ackResponse.split("\\|");

                    if (Integer.parseInt(ackParts[0]) == 3) {
                        System.out.println("ACK recebido do cliente " + cliente + ". Atendimento concluído.");
                    }
                }
          }  

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String readTaskFromJSON(int clientId) {
        try {
            JSONParser parser = new JSONParser();
            JSONObject jsonObject = (JSONObject) parser.parse(new FileReader("tasks.json"));
            JSONArray tasks = (JSONArray) jsonObject.get("tasks");
    
            for (Object obj : tasks) {
                JSONObject task = (JSONObject) obj;
                long taskClientId = (long) task.get("client_id");
                if (taskClientId == clientId) {
                    String taskId = (String) task.get("task_id");
                    String command = (String) task.get("command");
                    long frequency = (long) task.get("frequency");
    
                    return "task_id=" + taskId + ",command=" + command + ",frequency=" + frequency;
                }
            }
            
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }
        return "Nenhuma tarefa encontrada para o cliente " + clientId;
    }
    

    private String createTaskDatagram(int type, int seqNum, int clientId, String command) {
        int size = command.length();
        return type + "|" + seqNum + "|" + clientId + "|" + size + "|" + command;
    }

    private String createDatagramAckReg(int type, int sequenceNumber, int clientId) {
       //int size = info.length();
        return type + "|" + sequenceNumber + "|" + clientId;
    }

    public static void main(String[] args) {
        try {
            Server server = new Server();
            server.listen();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
