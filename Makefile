# Diretórios e biblioteca
SRC_DIR = .
BIN_DIR = bin
LIBRARY = myproject.jar:json-20240303.jar

# Arquivos-fonte e classes compiladas
SOURCES = $(SRC_DIR)/Client.java $(SRC_DIR)/AckHandle.java $(SRC_DIR)/Server.java $(SRC_DIR)/JasonUtil.java
CLASSES = $(BIN_DIR)/Client.class $(BIN_DIR)/AckHandle.class $(BIN_DIR)/Server.class $(BIN_DIR)/JasonUtil.class

# Comandos de compilação
JAVAC = javac
JAVA = java
JFLAGS = -cp ".:$(LIBRARY)" -d $(BIN_DIR)

# Alvo padrão: compila tudo
all: $(CLASSES)

# Regra para compilar cada classe
$(BIN_DIR)/%.class: $(SRC_DIR)/%.java
	@mkdir -p $(BIN_DIR)
	$(JAVAC) $(JFLAGS) $<

# Alvo para executar o servidor
server: all
	$(JAVA) -cp "$(BIN_DIR):$(LIBRARY)" Server

# Alvo para executar o cliente
client: all
	$(JAVA) -cp "$(BIN_DIR):$(LIBRARY)" Client

# Alvo para limpar os arquivos compilados
clean:
	rm -rf $(BIN_DIR)
