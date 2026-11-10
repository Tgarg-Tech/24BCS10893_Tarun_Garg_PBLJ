package com.backend.management.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import com.backend.management.model.Student;

public interface StudentRepository extends JpaRepository<Student, Integer> {

    List<Student> findByCourse(String course);

}
