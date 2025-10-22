CREATE TABLE url (
                     url	 VARCHAR(2048),
                     title	 VARCHAR(128) DEFAULT 'Page',
                     citation VARCHAR(256),
                     PRIMARY KEY(url)
);

CREATE TABLE words (
                       word VARCHAR(64),
                       PRIMARY KEY(word)
);

CREATE TABLE words_url (
                           words_word VARCHAR(64),
                           url_url	 VARCHAR(2048),
                           PRIMARY KEY(words_word,url_url)
);

CREATE TABLE url_url (
                         url_url	 VARCHAR(2048),
                         url_url1 VARCHAR(2048),
                         PRIMARY KEY(url_url,url_url1)
);

ALTER TABLE words_url ADD CONSTRAINT words_url_fk1 FOREIGN KEY (words_word) REFERENCES words(word);
ALTER TABLE words_url ADD CONSTRAINT words_url_fk2 FOREIGN KEY (url_url) REFERENCES url(url);
ALTER TABLE url_url ADD CONSTRAINT url_url_fk1 FOREIGN KEY (url_url) REFERENCES url(url);
ALTER TABLE url_url ADD CONSTRAINT url_url_fk2 FOREIGN KEY (url_url1) REFERENCES url(url);

