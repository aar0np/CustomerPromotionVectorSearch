# CustomerPromotionVectorSearch

A simple Restful service simulating a promotion call.  When a customer scans or adds a product to their cart,
the vector stored with that product is used to query an approximate nearest neighbor (ANN) to pull the _next_ nearest neighbor.

## Stack
 - Java 21
 - Spring Boot 2.7.12
 - Maven
 - Cassandra Java Driver 4.16.0

## Data Model

Create table:

    CREATE TABLE pet_supply_vectors (
        product_id TEXT PRIMARY KEY,
        product_name TEXT,
        product_vector vector<float, 14>);

Create index:

    CREATE CUSTOM INDEX ON pet_supply_vectors(product_vector) USING 'StorageAttachedIndex';

Insert data:

    INSERT INTO pet_supply_vectors (product_id, product_name, product_vector)
    VALUES ('pf1843','HealthyFresh - Chicken raw dog food',[1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0]);
    INSERT INTO pet_supply_vectors (product_id, product_name, product_vector)
    VALUES ('pf1844','HealthyFresh - Beef raw dog food',[1, 0, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0]);
    INSERT INTO pet_supply_vectors (product_id, product_name, product_vector)
    VALUES ('pt0021','Dog Tennis Ball Toy',[0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 1, 1, 0, 0]);
    INSERT INTO pet_supply_vectors (product_id, product_name, product_vector)
    VALUES ('pt0041','Dog Ring Chew Toy',[0, 0, 0, 1, 0, 0, 0, 1, 1, 1, 0, 0, 0, 0]);
    INSERT INTO pet_supply_vectors (product_id, product_name, product_vector)
    VALUES ('pf7043','PupperSausage Bacon dog Treats',[0, 0, 0, 1, 0, 0, 1, 0, 0, 0, 0, 0, 1, 1]);
    INSERT INTO pet_supply_vectors (product_id, product_name, product_vector)
    VALUES ('pf7044','PupperSausage Beef dog Treats',[0, 0, 0, 1, 0, 1, 1, 0, 0, 0, 0, 0, 1, 0]);

Data is built using a "bag of words" approach, with a vocabulary maxtrix built from all 14 words present in the above 6 product names, in the following order:

HealthyFresh, Chicken, raw, dog, food, Beef, Pupper-Sausage, Ring, Chew, Toy, Ball, Tennis, Bacon, Treats

If the product name contains that word, it gets a 1 for that entry.  If not, it has a 0 in that entry.

Query:

    SELECT * FROM pet_supply_vectors
    ORDER BY product_vector
    ANN OF [1, 0, 1, 1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0] LIMIT 2;

## Environment

Requires the following environment variables to be set:

    export ASTRA_DB_CLIENT_ID=token
    export ASTRA_DB_APP_TOKEN=AstraCS:s8adf7sPUTYOURTOKENHERE0dgu0asdf
    export ASTRA_DB_KEYSPACE=bigbox
    export ASTRA_DB_SECURE_BUNDLE_PATH=/Users/aaronploetz/scb/scb_vectorsearch.zip

The `ASTRA_DB_CLIENT_ID` var _must_ be the literal text "token".  The keyspace and secure bundle locations are also dependent on the name of the keyspace
in Astra DB, as well as the location of the downloaded secure bundle.  Adjust accordingly.

## Application

If necessary, the version of Java is set inside the pom.xml.

Assuming that Maven and Java are installed, the app should start with the following command:

    mvn spring-boot:run


