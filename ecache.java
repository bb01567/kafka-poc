Required Dependencies:
Include ehcache dependency in pom.xml

pom.xml
<dependency>
  <groupId>net.sf.ehcache</groupId>
  <artifactId>ehcache</artifactId>
  <version>2.9.0</version>
</dependency>
Complete pom.xml:
Including all spring boot and MySQL dependencies.

pom.xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.onlinetutorialspoint</groupId>
  <artifactId>Springboot_EHCache_Example</artifactId>
  <version>0.0.1-SNAPSHOT</version>
  <packaging>jar</packaging>
  <name>Springboot_EHCache_Example</name>
  <description>Spring Boot EhCache Example</description>
  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>2.0.5.RELEASE</version>
    <relativePath/> <!-- lookup parent from repository -->
  </parent>
  <properties>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <project.reporting.outputEncoding>UTF-8</project.reporting.outputEncoding>
    <java.version>1.8</java.version>
  </properties>
  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-cache</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-jdbc</artifactId>
    </dependency>
    <dependency>
      <groupId>net.sf.ehcache</groupId>
      <artifactId>ehcache</artifactId>
      <version>2.9.0</version>
    </dependency>
    <dependency>
      <groupId>mysql</groupId>
      <artifactId>mysql-connector-java</artifactId>
      <scope>runtime</scope>
    </dependency>
  </dependencies>
  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
Creating application.properties with all database related information, enabling the spring boot actuator endpoints to see the ehcache size details and spring cache declarations.

application.properties
# Database
server.port=8080
spring.datasource.driver-class-name: com.mysql.jdbc.Driver
spring.datasource.url: jdbc:mysql://localhost:3306/otp
spring.datasource.username: root
spring.datasource.password: 1234

management.endpoints.web.exposure.include=*
spring.cache.cache-names=itemCache
spring.cache.type=ehcache
spring.cache.ehcache.config=classpath:ehcache.xml
Creating an ehcache.xml file containing ehcache configuration.  Find more about ehcache configuration here.

ehcache.xml
<ehcache xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:noNameSpaceSchemaLocation="ehcache.xsd"
         updateCheck="true"
         monitoring="autodetect"
         dynamicConfig="true">
    <diskStore path="java.io.tmpdir"/>
    <cache name="itemCache"
            maxEntriesLocalHeap="1000"
            maxEntriesLocalDisk="1000"
            eternal="false"
            diskSpoolBufferSizeMB="20"
            timeToIdleSeconds="300"
            timeToLiveSeconds="600"
            memoryStoreEvictionPolicy="LFU"
            transactionalMode="off">
        <persistence strategy="localTempSwap"/>
    </cache>

</ehcache>
Configuring EhCache in Spring boot application: @EnableCaching annotation is used to enabling the caching feature declaratively.

I am creating EhCacheManagerFactory bean by passing the ehcache.xml configuration file.

EHCacheConfig.java
package com.onlinetutorialspoint.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.ehcache.EhCacheCacheManager;
import org.springframework.cache.ehcache.EhCacheManagerFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

@EnableCaching
@Configuration
public class EHCacheConfig {

    @Bean
    public CacheManager cacheManager(){
        return new EhCacheCacheManager(ehCacheManagerFactory().getObject());
    }

    @Bean
    public EhCacheManagerFactoryBean ehCacheManagerFactory(){
        EhCacheManagerFactoryBean ehCacheBean = new EhCacheManagerFactoryBean();
        ehCacheBean.setConfigLocation(new ClassPathResource("ehcache.xml"));
        ehCacheBean.setShared(true);
        return ehCacheBean;
    }

}
Creating Item rest controller, responsible for reading data from ItemCache.

ItemController.java
package com.onlinetutorialspoint.controller;

import com.onlinetutorialspoint.cache.ItemCache;
import com.onlinetutorialspoint.model.Item;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class ItemController {

    @Autowired
    ItemCache itemCache;
    @GetMapping("/item/{itemId}")
    @ResponseBody
    public ResponseEntity<Item> getItem(@PathVariable int itemId) throws Exception{
        Item item = itemCache.getItem(itemId);
        return new ResponseEntity<Item>(item, HttpStatus.OK);
    }

    @PutMapping("/updateItem")
    @ResponseBody
    public ResponseEntity<Item> updateItem(@RequestBody Item item){
        if(item != null){
            itemCache.updateItem(item);
        }
        return new ResponseEntity<Item>(item, HttpStatus.OK);
    }

    @DeleteMapping("/delete/{id}")
    @ResponseBody
    public ResponseEntity<Void> deleteItem(@PathVariable int id){
        itemCache.deleteItem(id);
        return new ResponseEntity<Void>(HttpStatus.ACCEPTED);
    }
}
Creating  ItemCache, responsible for reading items from a database using JdbcTemplate and making the method as @Cachable.

ItemCache.java
package com.onlinetutorialspoint.cache;

import com.onlinetutorialspoint.model.Item;
import com.onlinetutorialspoint.repo.ItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

@Component
public class ItemCache {

    @Autowired
    ItemRepository itemRepo;

    @Cacheable(value="itemCache", key="#id")
    public Item getItem(int id) throws Exception{
        System.out.println("In ItemCache Component..");
        return itemRepo.getItem(id);
    }

    @CacheEvict(value="itemCache",key = "#id")
    public int deleteItem(int id){
        System.out.println("In ItemCache Component..");
        return itemRepo.deleteItem(id);
    }

    @CachePut(value="itemCache")
    public void updateItem(Item item){
        System.out.println("In ItemCache Component..");
        itemRepo.updateItem(item);
    }
}
@Cachable: Is used to adding the cache behaviour to a method. We can also give the name to it, where the cache results would be saved.
@CacheEvict: Is used to remove the one or more cached values. allEntries=true parameter allows us to remove all entries from the cache.
@CachePut: Is used to update the cached value.

Creating an Item model

Item.java
package com.onlinetutorialspoint.model;

import java.io.Serializable;

public class Item implements Serializable {
    private int id;
    private String name;
    private String category;

    public Item() {
    }

    public Item(int id, String name, String category) {
        this.id = id;
        this.name = name;
        this.category = category;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
}
Creating Item repository

ItemRepository.java
package com.onlinetutorialspoint.repo;

import com.onlinetutorialspoint.model.Item;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ItemRepository {

    @Autowired
    JdbcTemplate template;

    /*Getting a specific item by item id from table*/
    public Item getItem(int itemId){
        String query = "SELECT * FROM ITEM WHERE ID=?";
        return template.queryForObject(query,new Object[]{itemId},new BeanPropertyRowMapper<>(Item.class));
    }

    /*delete an item from database*/
    public int deleteItem(int id){
        String query = "DELETE FROM ITEM WHERE ID =?";
        int size = template.update(query,id);
        return size;
    }

    /*update an item from database*/
    public void updateItem(Item item){
        String query = "UPDATE ITEM SET name=?, category=? WHERE id =?";
        template.update(query,
                new Object[] {
                        item.getName(),item.getCategory(), Integer.valueOf(item.getId())
                });
    }

}
Spring Boot main class

SpringBootApplication.java
package com.onlinetutorialspoint;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SpringbootApplication {

  public static void main(String[] args) {
    SpringApplication.run(SpringbootApplication.class, args);
  }
}
Run it:
Console
> mvn spring-boot:run

  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \