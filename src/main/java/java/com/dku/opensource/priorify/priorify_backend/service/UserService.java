package java.com.dku.opensource.priorify.priorify_backend.service;

import com.dku.opensource.priorify.priorify_backend.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Service;
import org.bson.types.ObjectId;

@Service
public interface UserService extends MongoRepository<User, ObjectId> {
    User findByName(String name);
} 