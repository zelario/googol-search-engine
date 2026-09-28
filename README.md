# Googol, a Distributed Web Search Engine

A distributed web search engine implementation using Java RMI that provides web crawling, indexing, and search capabilities across multiple nodes for fault tolerance and scalability.
You can find the full report on  **docs/** folder.

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
├── Log.java              # Logging utilities                   
└── web/                  # Spring Boot web interface (HTML UI)                     
           ├── Application.java          # Spring Boot application entry point                        
           ├── ClientService.java        # Service for client operations                     
           ├── ErrorController.java      # Handles error pages                       
           ├── FooterController.java     # Handles footer rendering                     
           ├── IndexController.java      # Handles index page                     
           ├── ResultsController.java    # Handles search results page                   
           ├── SearchController.java     # Handles search requests                          
           ├── StartupManager.java       # Manages startup logic                   
           ├── StatsController.java      # Handles statistics page                    
           └── WebSocketConfig.java      # WebSocket configuration                            

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
- **PostgreSQL** 
- **Maven** 

# Installation

1. **Install PostgreSQL**: Download from [https://www.postgresql.org/download/]
2. **Install Java 17**: Download JDK 17+ from [https://www.oracle.com/java/technologies/downloads/]
3. **Install Maven**: Download from [https://maven.apache.org/download.cgi]

# Setup Instructions

1. Create a `PostgreSQL` user that will own the barrel databases (postgres is default).
2. Navigate to the `config/` folder and alter `.env.example` to `.env`
3. Edit `.env` with your database connection details:              
   - HOST=`localhost`                        
   - PORT=`5432`                       
   - USERNAME=`username`                      
   - PASSWORD=`password`
   - OPEN_ROUTER_KEY=`key` (get your key at [https://openrouter.ai/])                      
4. (Optional) Change properties of server and Spring Boot:
   - `config/.properties`: backend/server related properties
   - `src/main/resources/application.properties`: Spring Boot related properties
5. Compile the project using Maven: `mvn clean install`
6. Run the app using: `mvn spring-boot:run`

# Usage

Open your browser and go to: `https://address:port` where you can:

- **Index**: Submit a URL to add it to the crawling queue
- **Search**: Enter search terms to find indexed pages
- **Statistics**: View system statistics and performance metrics

# Stopping the System

Use `Ctrl+C` in the terminal where the app is running to stop it gracefully.

# Data Management

- **Barrel Data**: Indexed data is stored in your PostgreSQL databases.
- **URL Queue**: The queue of URLs to crawl is automatically saved and loaded from `data/urlQueue.ser`.
- **Gateway**: Gateway state is automatically saved and loaded from `data/gateway.ser`.

# Monitoring

- **Logs**: Check console output for system status and errors
- **Statistics**: Use the client's stats interface to monitor performance
- **Database**: Query the PostgreSQL databases directly for detailed information

# Common Issues

1. **Database Connection Errors**:
   - Verify PostgreSQL is running
   - Check `.env` configuration
2. **RMI Connection Issues**:
   - Check that components are started in the correct order
   - Verify no port conflicts exist
   - Ensure firewall allows RMI communication
3. **Build Issues**:
   - Verify Java 17+ is installed
   - Check Maven dependencies are downloaded
   - Clear `target/` directory and rebuild

# Authorship

- **José Amado**
- **José Silva**
