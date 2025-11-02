# Googol, a Distributed Web Search Engine

A distributed web search engine implementation using Java RMI that provides web crawling, indexing, and search capabilities across multiple nodes for fault tolerance and scalability.

# Project Structure

src/main/java/projetosd/                                  
├── Gateway.java          # Central coordination service                                        
├── UrlQueue.java         # URL management and distribution                        
├── Barrel.java           # Index storage and search                            
├── Downloader.java       # Web crawling and parsing                       
├── Client.java           # User interface                      
├── Config.java           # System configuration                        
├── Database.java         # Database connection management                       
├── Stats.java            # Performance statistics                       
├── Page.java             # Web page data model                             
└── Log.java              # Logging utilities                                 

# Architecture Components

- **Gateway** - Central coordination service that handles client requests and load balances across barrels
- **URL Queue** - Manages the queue of URLs to be crawled and distributed to downloaders  
- **Barrels** - Index servers that store and search the web content in PostgreSQL databases
- **Downloaders** - Web crawlers that fetch, parse, and index web pages
- **Client** - Command-line interface for users to search, add URLs and use other features

# System Flow

1. **URLs** are added to the URL Queue 
2. **Downloaders** fetch URLs from the queue, download and parse web pages
3. **Multicasting** distributes indexed content across all Barrels for redundancy
4. **Gateway** handles search requests by load balancing across available Barrels
5. **Synchronization** ensures data consistency between barrel replicas

# Pre-requisites

- **Java 17** or higher
- **PostgreSQL** database server
- **Maven** (optional)

# Installation

1. **Install PostgreSQL**: Download from [https://www.postgresql.org/download/]
2. **Install Java 17**: Download JDK 17+ from [https://www.oracle.com/java/technologies/downloads/]

# Setup Instructions

1. Create PostgreSQL databases for each barrel (defaultly named: `Barrel1100` and `Barrel1101`)
2. Run the database initialization scripts: `create_database` and `stop_words_triggers`
3. Navigate to the `config/` folder and alter `.env.example` to `.env`
4. Edit `.env` with your database connection details:                       
   DB1100_HOSTNAME=`your_barrel_1100_host`                         
   DB1100_PORT=`5432`                               
   DB1100_NAME=`Barrel1100`                            
   DB1100_USERNAME=`your_username`                             
   DB1100_PASSWORD=`your_password`                              

   DB1101_HOSTNAME=`your_barrel_1101_host`                           
   DB1101_PORT=`5432`                            
   DB1101_NAME=`Barrel1101`                            
   DB1101_USERNAME=`your_username`                         
   DB1101_PASSWORD=`your_password`     
5. Compile the project using Maven or Javac (if you are in VS Code you can also run the task: "Compile Project")
6. Run each component in the order: `Gateway`, `URL Queue`, `Barrels`, `Downloaders`, `Clients`.

# Usage

Once the client is running, you can use these commands:

- **Index**: Enter a URL to add it to the crawling queue
- **Search**: Enter search terms to find indexed pages
- **Filter**: Choose a filter for the searches
- **Backlinks**: View pages that link to a specific URL
- **Statistics**: View system statistics and performance metrics
- **Exit**: Quit the client

# Stopping the System

Use `Ctrl+C` in each component's terminal to stop it gracefully

# Data Management

- **Barrel Data**: Run `scripts/delete_data.sql` in your databases
- **Url Queue**: The URL queue automatically saves/loads its state from `data/urlQueue.ser`
- **Stats**: The Gateway automatically saves/loads the stats from `data/stats.ser`

# Monitoring

- **Logs**: Check console output for system status and errors
- **Statistics**: Use the client's stats command to monitor performance
- **Database**: Query the PostgreSQL databases directly for detailed information

# Common Issues

1. **Database Connection Errors**:
   - Verify PostgreSQL is running
   - Check `.env` configuration
   - Ensure databases exist and are accessible
2. **RMI Connection Issues**:
   - Check that components are started in the correct order
   - Verify no port conflicts exist
   - Ensure firewall allows RMI communication
3. **Build Issues**:
   - Verify Java 17+ is installed
   - Check Maven dependencies are downloaded
   - Clear `target/` directory and rebuild
