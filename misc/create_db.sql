CREATE TABLE url (
                     url	 VARCHAR(2048),
                     title	 VARCHAR(128) NOT NULL DEFAULT 'Page',
                     citation VARCHAR(256) NOT NULL,
                     PRIMARY KEY(url)
);

CREATE TABLE url_url (
                         url_url	 VARCHAR(2048),
                         url_url1 VARCHAR(2048),
                         PRIMARY KEY(url_url,url_url1)
);

ALTER TABLE url_url ADD CONSTRAINT url_url_fk1 FOREIGN KEY (url_url) REFERENCES url(url);
ALTER TABLE url_url ADD CONSTRAINT url_url_fk2 FOREIGN KEY (url_url1) REFERENCES url(url);