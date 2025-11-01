1. If not installed, install postgres for the desired OS: https://www.postgresql.org/download/
2. Use the script in /scripts to initialize the 2 barrels for execution
3. Use the 'create_database.sql' script, found in /scripts to populate the database with the required tables and constraints
4. Also run the 'stop_words_trigger.sql' file, found in the same folder
5. Navigate to the /config folder and create a .env based on the .env.example found in the same folder
6. All configuration is done, run the programs in this order: UrlQueue, Gateway, Barrel #1, Barrel #2, Downloader #1, Downloader #2, Client
7. Ready to go
8. OPTIONAL: to clean all data in the database use the 'delete_data.sql' in the scripts folder
9. OPTIONAL: to visualize the database ER go to onda.dei.uc.pt and load 'diagram.json' found in /scripts