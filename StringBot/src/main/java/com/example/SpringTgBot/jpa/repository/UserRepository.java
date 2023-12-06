package com.example.SpringTgBot.jpa.repository;

import com.example.SpringTgBot.jpa.entity.User;
import org.springframework.data.repository.CrudRepository;

public interface UserRepository extends CrudRepository<User, Long> {

}
