SlimeWorldManager - Suporte nativo a PostgreSQL

Uma versão modificada do SlimeWorldManager com suporte nativo a PostgreSQL, desenvolvida para servidores que precisam de maior escalabilidade e confiabilidade no armazenamento de mundos.

🚀 Sobre o Projeto

O SlimeWorldManager original foi criado para gerenciar mundos Slime de forma eficiente.
Esta versão adiciona:

✅ Suporte completo a PostgreSQL
✅ Melhor escalabilidade para redes grandes
✅ Melhor controle de conexões
✅ Ideal para servidores com múltiplas instâncias (ex: BedWars, Duels, etc.)

Se você pretende escalar seu servidor para centenas de jogadores simultâneos, utilizar PostgreSQL é muito mais robusto do que armazenamento local ou SQLite.

🛠 Tecnologias Utilizadas

Java 8
PostgreSQL 12+
Maven
Spigot / Paper 1.8+

⚙️ Configuração do PostgreSQL

Exemplo de configuração no config.yml:

postgresql:
    enabled: true
    host: localhost
    port: 5432
    username: postgres
    password: suadb
    database: postgres
    
🏧 Criando o Banco de Dados

1. Baixar o PostgreSQL em seu computador: https://www.postgresql.org/download/
2. Você pode utilizar o PGAdmin para gerenciar suas databases dentro do PostgreSQL.
(https://www.pgadmin.org/download/)

📦 Build do Projeto

mvn clean package
O .jar será gerado na pasta:
/target/

📌 Compatibilidade

Minecraft 1.8.x
Spigot 1.8.8
PaperSpigot 1.8.8
Java 8+

⚠️ Aviso

Este projeto é um fork não oficial do SlimeWorldManager.
Certifique-se de respeitar a licença original do projeto.

🤝 Contribuições

Pull Requests são bem-vindos!
Se encontrar bugs ou quiser sugerir melhorias, abra uma issue.

📜 Licença

Este projeto segue a mesma licença do projeto original.
