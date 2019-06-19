package com.melardev.cloud.proxy.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.HashMap;


@RestController
@RequestMapping(value = "/todos", produces = MediaType.APPLICATION_JSON_VALUE)
public class TodosProxyController {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    ObjectMapper objectMapper;

    private static HttpEntity<String> getHeadersRequestEntity() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);
        return new HttpEntity<>(headers);
    }

    private static HttpEntity<String> getWriteRequestEntity(String requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", MediaType.APPLICATION_JSON_VALUE);
        headers.set("Content-Type", MediaType.APPLICATION_JSON_VALUE);

        return new HttpEntity<>(requestBody, headers);
    }

    private ResponseEntity<String> fetch(String url) {
        return fetch(url, getHeadersRequestEntity());
    }

    private ResponseEntity<String> fetch(String url, HttpEntity requestEntity) {
        return fetch(url, HttpMethod.GET, requestEntity);
    }

    private ResponseEntity<String> fetch(String url, HttpMethod httpMethod) {
        return fetch(url, httpMethod, getHeadersRequestEntity());
    }


    private ResponseEntity<String> fetch(String url, HttpMethod httpMethod, HttpEntity requestEntity) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(url,
                    httpMethod, requestEntity, String.class);
            return response;
        } catch (RestClientException ex) {
            String response;
            HttpStatus statusCode = HttpStatus.INTERNAL_SERVER_ERROR;
            if (ex instanceof RestClientResponseException) {
                response = ((RestClientResponseException) ex).getResponseBodyAsString();
                if (ex instanceof HttpStatusCodeException)
                    statusCode = ((HttpStatusCodeException) ex).getStatusCode();
            } else
                response = ex.getMessage();
            return new ResponseEntity<>(response, statusCode);
        }
    }

    @HystrixCommand(fallbackMethod = "fallbackResponse", commandProperties = {
            @HystrixProperty(name = "circuitBreaker.requestVolumeThreshold", value = "20"),
            @HystrixProperty(name = "circuitBreaker.errorThresholdPercentage", value = "50"), // default already 50
            @HystrixProperty(name = "execution.isolation.thread.timeoutInMilliseconds", value = "1000") // default already 1000
    })
    @GetMapping
    public ResponseEntity<String> index() {
        return fetch("http://todo-service/");
    }

    @HystrixCommand(fallbackMethod = "fallbackResponse")
    @GetMapping("/{id}")
    public ResponseEntity<String> get(@PathVariable("id") Long id) {
        return fetch("http://todo-service/api/todos/" + id);
    }

    @HystrixCommand(fallbackMethod = "fallbackResponse")
    @GetMapping("/pending")
    public ResponseEntity<String> getNotCompletedTodos() {
        return fetch("http://todo-service/pending");
    }

    @HystrixCommand(fallbackMethod = "fallbackResponse")
    @GetMapping("/completed")
    public ResponseEntity<String> getCompletedTodos() {
        return fetch("http://todo-service/completed");
    }

    @HystrixCommand(fallbackMethod = "fallbackResponse")
    @PostMapping
    public ResponseEntity<String> create(@RequestBody String todo) {
        String url = "http://todo-service/api/todos";
        return fetch(url, HttpMethod.POST, getWriteRequestEntity(todo));
    }

    @HystrixCommand(fallbackMethod = "fallbackResponse")
    @PutMapping("/{id}")
    public ResponseEntity update(@PathVariable("id") Long id,
                                 @RequestBody String todo) {
        String url = "http://todo-service/" + id;
        return fetch(url, HttpMethod.PUT, getWriteRequestEntity(todo));
    }

    @HystrixCommand(fallbackMethod = "fallbackResponse")
    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable("id") Long id) {
        String url = "http://todo-service/" + id;
        return fetch(url, HttpMethod.DELETE);
    }

    @HystrixCommand(fallbackMethod = "fallbackResponse")
    @DeleteMapping
    public ResponseEntity<String> deleteAll() {
        String url = "http://todo-service/";
        return fetch(url, HttpMethod.DELETE);
    }

    @HystrixCommand(fallbackMethod = "fallbackResponse")
    @GetMapping(value = "/after/{date}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getByDateAfter(@PathVariable("date") String date) {
        return fetch("http://todo-service/after/" + date);
    }

    @HystrixCommand(fallbackMethod = "fallbackResponse")
    @GetMapping(value = "/before/{date}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getByDateBefore(@PathVariable("date") String date) {
        return fetch("http://todo-service/after/" + date);
    }

    public ResponseEntity<String> fallbackResponse() {
        HashMap<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("full_messages", Arrays.asList("Server is down"));

        String responseStr;
        try {
            responseStr = objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            responseStr = "{success: false}";
        }

        return new ResponseEntity<String>(responseStr, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
