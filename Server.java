import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
import org.json.JSONObject;

public class Server {
    private static final int PORT = 12345;
    private DatagramSocket socket;
    private List<Integer> clients = new ArrayList<>();
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

            if (type == 1) {
                int newClientId = clientIdCounter.getAndIncrement();
                clients.add(newClientId);

                System.out.println("Registei o id " + clients.get(newClientId - 1));

                System.out.println("Cliente registado. Foi-lhe atribuído o ID: " + newClientId);

                String ackMessage = createDatagramAckReg(3, sequenceNumber, newClientId);
                DatagramPacket ackPacket = new DatagramPacket(
                        ackMessage.getBytes(), ackMessage.length(), clientAddress, clientPort
                );
                socket.send(ackPacket);
            }

            if (type == 2) {
                if (parts[2] == null || parts[2].isEmpty()) return;

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
            
            if (type == 5) {
                int clientId = Integer.parseInt(parts[2]);
                String result = parts[3];

                System.out.println("Resultado recebido do cliente " + clientId + ":" + result);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String readTaskFromJSON(int clientId) {
        try {
            String conteudo = new String(Files.readAllBytes(Paths.get("tasks.json")));
            JSONObject jsonObject = new JSONObject(conteudo);
            JSONArray tasks = jsonObject.getJSONArray("tasks");
    
            for (int i = 0; i < tasks.length(); i++) {
                JSONObject task = tasks.getJSONObject(i);
                int taskClientId = task.getInt("client_id");
                if (taskClientId == clientId) {
                    String taskId = task.getString("task_id");
                    String command = task.getString("command");
                    int frequency = task.getInt("frequency");
    
                    return "task_id=" + taskId + ",command=" + command + ",frequency=" + frequency;
                }   
            }
            
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
    

    private String createTaskDatagram(int type, int seqNum, int clientId, String command) {
        int size = command.length();
        return type + "|" + seqNum + "|" + clientId + "|" + size + "|" + command;
    }

    private String createDatagramAckReg(int type, int sequenceNumber, int clientId) {
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