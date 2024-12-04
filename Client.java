    import java.io.BufferedReader;
    import java.io.IOException;
    import java.io.InputStreamReader;
    import java.net.DatagramPacket;
    import java.net.DatagramSocket;
    import java.net.InetAddress;
    import java.util.concurrent.Executors;
    import java.util.concurrent.ScheduledExecutorService;
    import java.util.concurrent.TimeUnit;
    import java.util.concurrent.atomic.AtomicInteger;


    // Falta implementar a retransmissão no cliente. Caso alguma mensagem enviado pelo cliente não chegue ao servidor. Ou seja, não recebemos o ACK. O cliente deve voltar a enviar a sua mensagem e, só depois de recebido o ACK, prosseguir para a próxima etapa.
    // Falta implementar o medir a CPU e o medir a RAM

    //ADICIONAL: spam de clientes não registados.. como

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

                String registrationMessage = utils.criaDatagramaRegisto(1, newSequenceNumber); 

                DatagramPacket registrationPacket = new DatagramPacket(
                        registrationMessage.getBytes(), registrationMessage.length(), serverAddress, SERVER_PORT
                );
                socket.send(registrationPacket);
                System.out.println("Pedido de Registo enviado.");

                ackHandle.criaAckPendente(newSequenceNumber, 200, serverAddress, SERVER_PORT, registrationPacket);
                
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
                    ackHandle.processAckReg(newSequenceNumber);
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

                ackHandle.criaAckPendente(newSequenceNumber, clientId, serverAddress, SERVER_PORT, taskPacket);


                // Aqui ele recebe a tarefa
                byte[] buffer = new byte[1024];
                DatagramPacket responsePacket = new DatagramPacket(buffer, buffer.length);
                socket.receive(responsePacket);
                
                String responseMessage = new String(responsePacket.getData(), 0, responsePacket.getLength());
                String[] parts = responseMessage.split("\\|");

                int tipo = Integer.parseInt(parts[0]);
                int sequencia = Integer.parseInt(parts[1]);
                int taskClientId = Integer.parseInt(parts[2]);
                String comando = parts[4];

                
                if (4 == tipo && taskClientId == clientId) {

                    ackHandle.processAck(sequencia, clientId);

                    String[] comandoPartes = comando.split(",");
                    String taskId = null, codigo = null;
                    int frequencia = 0;
                    int tipoTarefa = 0;
                    long limite = 0;
        
                    for (String part : comandoPartes) {
                        if (part.startsWith("task_id=")) {
                            taskId = part.split("=")[1];
                        } else if (part.startsWith("tipo_tarefa=")) {
                            tipoTarefa = Integer.parseInt(part.split("=")[1]);
                        } else if (part.startsWith("command=")) {
                            codigo = part.split("=")[1];
                        } else if (part.startsWith("frequency=")) {
                            frequencia = Integer.parseInt(part.split("=")[1]);
                        } else if (part.startsWith("limit=")) {
                                limite = Long.parseLong(part.split("=")[1]);
                        }
                    }

                    if (taskId != null && codigo != null && frequencia >= 0) {  
                        System.out.println("Recebi a tarefa: " + codigo + " com frequência de " + frequencia + " segundos. e tipo: " + tipoTarefa);

                        ackHandle.sendAck(newSequenceNumber, clientId, serverAddress, SERVER_PORT, socket); 
                        
                        System.out.println("ACK enviado ao servidor.");

                        // Executa a tarefa recebida    
                        executeTask(codigo, frequencia, tipoTarefa, limite);       

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

        private void executeTask(String comando, int frequency, int tipoTarefa, long limite) {
            ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        
            if (frequency == 0) {
                try {
                    executeCommand(comando);
                    System.out.println("Servidor iPerf iniciado.");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            executor.scheduleAtFixedRate(() -> {
                try {
                    String result = null;
                    switch (tipoTarefa) {
                        case 1: // Ping
                            result = parsePingOutput(executeCommand(comando));
                            if ("Latência não encontrada.".equals(result)) break;
                            
                            String latencyString = result.replace("Latência média: ", "").replace(" ms", "").trim();
                            double latencia = Double.parseDouble(latencyString);

                            if (latencia>limite) {
                                System.out.println("Limite excedido! Latência: " + latencia + " ms, Limite: " + limite + " ms");
                                executor.shutdown();
                                System.exit(0);
                                return; // Encerra o processo
                            }

                            break;
                        case 2: // iPerf servidor
                            executeCommand(comando);
                            System.out.println("Servidor iPerf iniciado.");
                            break;
                        case 3: // iPerf
                            result = parseIperfOutput(executeCommand(comando));
                            
                            if ("Largura de banda não encontrada.".equals(result)) break;

                            String auxIperf = result.split(":")[1].trim();
                            double banda = convertToGbits(auxIperf);

                            if (banda>limite) {
                                System.out.println("Alerta: Banda Larga excedeu o limite.");
                                executor.shutdown();
                                System.exit(0);
                            }

                            break;
                        case 4: 
                            result = parseRAMOutput(executeCommand(comando));

                            if ("Informação de RAM não encontrada.".equals(result)) break;

                            int percentIndex = result.indexOf("%"); // Localiza o índice de "%"
                            String aux = result.substring(result.lastIndexOf(" ")+1, percentIndex);

                            double alerta = Double.parseDouble(aux); // dá erro porque o ram vem em um float
                            if (alerta > limite) {
                                System.out.println("Alerta: Uso de RAM excedeu o limite.");
                                executor.shutdown();
                                System.exit(0);
                            }
                            break;
                        default:
                            result = "Tipo de tarefa desconhec  ido.";
                    }
        
                    // Envia o resultado ao servidor
                    if (tipoTarefa == 1 || tipoTarefa == 3 || tipoTarefa == 4) sendTaskResult(result);


        
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }, 0, frequency, TimeUnit.SECONDS);
        }

        private String parsePingOutput(String output) {
            String[] lines = output.split("\n");
            for (String line : lines) {
                if (line.contains("rtt")) {
                    String[] rttParts = line.split("=");
                    if (rttParts.length > 1) {
                        String[] values = rttParts[1].trim().split("/");
                        return "Latência média: " + values[1] + " ms";
                    }
                }
            }
            return "Latência não encontrada.";
        }

        private String parseIperfOutput(String output) {

            System.out.println("OUTPUT: " + output);
            // Divida a saída em linhas
            String[] lines = output.split("\n");
            
            // Percorra cada linha para buscar a que contém "bits/sec"
            for (String line : lines) {
                if (line.contains("bits/sec")) {
                    try {
                        // Normalize os espaços e tente capturar a largura de banda
                        String normalizedLine = line.trim().replaceAll("\\s+", " ");
                        String regex = "(\\d+(\\.\\d+)?\\s*[KMG]?bits/sec)";
                        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(regex).matcher(normalizedLine);
                        if (matcher.find()) {
                            return "Largura de banda: " + matcher.group(1);
                        }
                    } catch (Exception e) {
                        // Log para depuração
                        System.err.println("Erro ao analisar a linha: " + line);
                        e.printStackTrace();
                    }
                }
            }
            return "Largura de banda não encontrada.";
        }
      
        private String parseRAMOutput(String output) {
            String[] lines = output.split("\n");
            for (String line : lines) {
                if (line.startsWith("Mem:")) { // Encontra a linha que começa com "Mem:"
                    String[] values = line.split("\\s+"); // Divide pelos espaços em branco
                    if (values.length > 2) {
                        // Obtém valores de RAM total e usada
                        String totalStr = values[1]; // Segundo valor (índice 1) é 'total'
                        String usedStr = values[2];  // Terceiro valor (índice 2) é 'used'
        
                        // Remove possíveis sufixos como "Gi", "Mi", etc.
                        double total = convertToGiBytes(totalStr);
                        double used = convertToGiBytes(usedStr);
        
                        // Calcula a percentagem utilizada
                        double percentUsed = (used / total) * 100;
                        percentUsed = Math.round(percentUsed * 100.0) / 100.0; // Arredonda para 2 casas decimais
        
                        return "RAM utilizada: " + percentUsed + "%";
                    }
                }
            }
            return "Informação de RAM não encontrada.";
        }
        
        // Função auxiliar para converter valores legíveis em bytes para números em GiB
        private double convertToGiBytes(String value) {
            double multiplier = 1.0; // Default é GiB
            if (value.endsWith("Mi")) {
                multiplier = 1.0 / 1024; // Converte Mi para Gi
                value = value.replace("Mi", "");
            } else if (value.endsWith("Gi")) {
                value = value.replace("Gi", "");    
            }
            return Double.parseDouble(value) * multiplier;
        }

        /* 
        private String parseRAMOutput(String output) {
            String[] lines = output.split("\n");
            for (String line : lines) {
                if (line.startsWith("Mem:")) { // Encontra a linha que começa com "Mem:"
                    String[] values = line.split("\\s+"); // Divide pelos espaços em branco
                    if (values.length > 2) {
                        return "RAM utilizada: " + values[2]; // O terceiro valor (índice 2) é o 'used'
                    }
                }
            }
            return "Informação de RAM não encontrada.";
        }
*/
private double convertToGbits(String value) {
    double multiplier = 1.0; // Default é Gbits
    if (value.endsWith("Mbits/sec")) {
        multiplier = 1.0 / 1000; // Converte Mbits para Gbits
        value = value.replace("Mbits/sec", "").trim();
    } else if (value.endsWith("Kbits/sec")) {
        multiplier = 1.0 / 1000000; // Converte Kbits para Gbits
        value = value.replace("Kbits/sec", "").trim();
    } else if (value.endsWith("Gbits/sec")) {
        value = value.replace("Gbits/sec", "").trim();
    }
    return Double.parseDouble(value) * multiplier;
}

    private String executeCommand(String command) {
        StringBuilder output = new StringBuilder();
        try {
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command(command.split(" ")); // Divide o comando em partes
            
            // Executa o comando
            Process process = processBuilder.start();

            // Captura a saída (stdout)
            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            // Captura possíveis erros (stderr)
            try (var errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = errorReader.readLine()) != null) {
                    output.append("ERROR: ").append(line).append("\n");
                }
            }

            // Aguarda o término do processo
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                output.append("Comando finalizado com código de erro: ").append(exitCode).append("\n");
            }

        } catch (Exception e) {
            output.append("Erro ao executar o comando: ").append(e.getMessage());
        }

        return output.toString();
    }

        private void sendTaskResult(String result) {
            try {
                int newSequenceNumber = sequenceNumber.getAndIncrement();  // sequencia 3
                String resultMessage = utils.criaDatagramaResultado(5, newSequenceNumber, clientId, result);

                DatagramPacket resultPacket = new DatagramPacket(
                        resultMessage.getBytes(), resultMessage.length(), serverAddress, SERVER_PORT
                );
                socket.send(resultPacket);
                
                ackHandle.criaAckPendente(newSequenceNumber, clientId, serverAddress, SERVER_PORT, resultPacket);

                
                System.out.println("Resultado enviado ao servidor: " + result);

                           
                // Aqui ele recebe a tarefa
                byte[] buffer2 = new byte[1024];
                DatagramPacket responsePacket2 = new DatagramPacket(buffer2, buffer2.length);
                socket.receive(responsePacket2);
                
                        String responseMessage2 = new String(responsePacket2.getData(), 0, responsePacket2.getLength());
                        String[] parts2 = responseMessage2.split("\\|");

                        int tipo2 = Integer.parseInt(parts2[0]);    
                        int sequencia2 = Integer.parseInt(parts2[1]);
                        int taskClientId2 = Integer.parseInt(parts2[2]);

                    if (tipo2 == 3) ackHandle.processAck(sequencia2, taskClientId2); 
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    // passar o arg com o nome do servidor e no localhost passar o nome que foi passado
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