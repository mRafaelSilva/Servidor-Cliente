public class DatagramUtils {
    
    //Criar datagrama que envia a tarefa
    public String criaDatagramaTarefa(int type, int seqNum, int clientId, String command) {
        int size = command.length();
        return type + "|" + seqNum + "|" + clientId + "|" + size + "|" + command;
    }

    //Criar datagrama que envia a resposta no registo
    public String criaDatagramaAckRegisto(int type, int sequenceNumber, int clientId) {
        return type + "|" + sequenceNumber + "|" + clientId;
    }

    //Criar datagrama que envia o resultado da execução
    public String criaDatagramaResultado(int type, int seqNum, int clientId, String result) {
        return type + "|" + seqNum + "|" + clientId + "|" + result;
    }

    public String criaDatagramaAck(int type, int seqNum, int clientId) {
        return type + "|" + seqNum + "|" + clientId;
    }

    public String criaDatagramaRegisto(int type, int sequenceNumber) {
        return type + "|" + sequenceNumber;
    }


    public String criaDatagramaPedirTarefaString(int type, int sequenceNumber, Integer clientId) {
        return type + "|" + sequenceNumber + "|"+ clientId;
    }
}
