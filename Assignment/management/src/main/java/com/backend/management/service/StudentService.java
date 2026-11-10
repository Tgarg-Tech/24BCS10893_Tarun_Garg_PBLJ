package com.backend.management.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.backend.management.model.Student;
import com.backend.management.repository.StudentRepository;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor  
public class StudentService {
    StudentRepository studentRepository;

    public Student saveOrUpdate(Student student) {
        return studentRepository.save(student);
    }

    public List<Student> findAllStudents() {
        return studentRepository.findAll();
    }

    public Student findStudentById(int id) {
        return studentRepository.findById(id).orElse(null);
    }

    public void deleteStudentById(int id) {
        studentRepository.deleteById(id);
    }

    public List<Student> findStudentsByCourse(String course) {
        return studentRepository.findByCourse(course);
    }
}
