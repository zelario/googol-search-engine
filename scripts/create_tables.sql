CREATE TABLE url (
                     url	 VARCHAR(2048),
                     title	 VARCHAR(128) DEFAULT 'Page',
                     citation	 VARCHAR(256) DEFAULT '',
                     updated_at TIMESTAMP NOT NULL DEFAULT now(),
                     PRIMARY KEY(url)
);

CREATE TABLE words (
                       word	 VARCHAR(64),
                       created_at TIMESTAMP NOT NULL DEFAULT now(),
                       PRIMARY KEY(word)
);

CREATE TABLE words_url (
                           words_word VARCHAR(64),
                           url_url	 VARCHAR(2048),
                           created_at TIMESTAMP NOT NULL DEFAULT now(),
                           PRIMARY KEY(words_word,url_url)
);

CREATE TABLE url_url (
                         url_url	 VARCHAR(2048),
                         url_url1 VARCHAR(2048),
                         created_at TIMESTAMP NOT NULL DEFAULT now(),
                         PRIMARY KEY(url_url,url_url1)
);

CREATE TABLE stop_words (
                        word VARCHAR(512),
                        created_at TIMESTAMP NOT NULL DEFAULT now(),
                        PRIMARY KEY(word)
);