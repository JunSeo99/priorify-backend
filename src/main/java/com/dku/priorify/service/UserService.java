package com.dku.priorify.service;

import com.dku.priorify.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Service;
import org.bson.types.ObjectId;

@Service
public interface UserService extends MongoRepository<User, ObjectId> {
    User findByName(String name);
} 