# Googol, a Distributed Web Search Engine

A distributed web search engine implementation using Java RMI that provides web crawling, indexing, and search capabilities across multiple nodes for fault tolerance and scalability.

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

1. **Java 17** or higher
2. **PostgreSQL** database server
3. **Maven** (optional)

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

### 3. Build the Project

Using Maven:
```bash
mvn clean compile
```

Or using the provided tasks in VS Code:
- Run the "Compile Project" task

## 🚀 Running the System

### Option 1: Using VS Code Tasks (Recommended)

1. **Compile and Run All**: Execute the "Compile and Run All" task
2. This will start all components in the correct order

### Option 2: Manual Startup

Start the components in this **exact order**:

1. **URL Queue**:
   ```bash
   java -cp "target/classes;path/to/dependencies" projetosd.UrlQueue
   ```

2. **Gateway**:
   ```bash
   java -cp "target/classes;path/to/dependencies" projetosd.Gateway
   ```

3. **Barrels** (start multiple instances):
   ```bash
   java -cp "target/classes;path/to/dependencies" projetosd.Barrel
   ```

4. **Downloaders** (start multiple instances):
   ```bash
   java -cp "target/classes;path/to/dependencies" projetosd.Downloader
   ```

5. **Client**:
   ```bash
   java -cp "target/classes;path/to/dependencies" projetosd.Client
   ```

### Option 3: Using VS Code Individual Tasks

- "Run UrlQueue"
- "Run Gateway"  
- "Run Barrel" (run multiple times for redundancy)
- "Run Downloader" (run multiple times for performance)
- "Run Client"

## 🎯 Usage

### Client Commands

Once the client is running, you can use these commands:

- **Add URL for indexing**: Enter a URL to add it to the crawling queue
- **Search**: Enter search terms to find indexed pages
- **Backlinks**: View pages that link to a specific URL
- **Statistics**: View system statistics and performance metrics
- **Exit**: Quit the client

### Search Features

- **Multi-term search**: Search for multiple keywords
- **Pagination**: Browse through search results (10 results per page)
- **Domain filtering**: Filter results by specific domains
- **Ranking**: Results ranked by relevance and backlinks

## 🔧 System Management

### Stopping the System

- **Stop All**: Use the "Stop All" task in VS Code
- **Manual**: Use `Ctrl+C` in each component's terminal

### Data Management

- **Clear all data**: Run `scripts/delete_data.sql` in your databases
- **Backup**: The URL queue automatically saves/loads its state from `data/urlQueue.ser`

### Monitoring

- **Logs**: Check console output for system status and errors
- **Statistics**: Use the client's stats command to monitor performance
- **Database**: Query the PostgreSQL databases directly for detailed information

## 📊 Database Schema

The system uses the following main tables:

- **url**: Stores webpage URLs, titles, and citations
- **words**: Dictionary of indexed words
- **words_url**: Many-to-many relationship between words and URLs
- **url_url**: Tracks links between pages (for backlink analysis)
- **stop_words**: Common words to exclude from indexing

### ER Diagram

Visualize the database structure at [https://onda.dei.uc.pt](https://onda.dei.uc.pt) by loading `scripts/diagram.json`.

## 🏛️ System Features

### Fault Tolerance
- **Redundant Barrels**: Multiple barrel instances ensure availability
- **Automatic Sync**: Gateway synchronizes data between barrels
- **Retry Logic**: Automatic retry mechanisms for failed operations

### Performance
- **Load Balancing**: Gateway distributes search requests across barrels  
- **Parallel Processing**: Multiple downloader threads for concurrent crawling
- **Efficient Indexing**: Optimized database queries and indexing strategies

### Scalability
- **Horizontal Scaling**: Add more barrels and downloaders as needed
- **Distributed Architecture**: Components can run on different machines
- **Asynchronous Processing**: Non-blocking operations where possible

## 📝 Development

### Project Structure

```
src/main/java/projetosd/
├── Gateway.java           # Central coordination service
├── UrlQueue.java         # URL management and distribution
├── Barrel.java           # Index storage and search
├── Downloader.java       # Web crawling and parsing
├── Client.java           # User interface
├── Config.java           # System configuration
├── Database.java         # Database connection management
├── Stats.java            # Performance statistics
├── Page.java             # Web page data model
└── Log.java              # Logging utilities
```

### Building from Source

```bash
# Compile
mvn compile

# Generate JavaDocs
mvn javadoc:javadoc

# Run tests (if available)
mvn test
```

### Documentation

- **JavaDocs**: Generated documentation available in `javadocs/`
- **API Reference**: See interface files for RMI method signatures
- **Database Schema**: Refer to `scripts/create_database.sql`

## ⚠️ Troubleshooting

### Common Issues

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

### Getting Help

- Check the logs for detailed error messages
- Verify all prerequisites are installed correctly
- Ensure the startup order is followed exactly

## 👥 Contributing

This is a university project for the Distributed Systems course. The system demonstrates key distributed computing concepts including RMI, fault tolerance, load balancing, and data replication.

## 📄 License

This project is developed for educational purposes as part of a Distributed Systems university course.