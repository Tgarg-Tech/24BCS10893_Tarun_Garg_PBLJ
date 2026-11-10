package com.backend.management.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.backend.management.model.Student;
import com.backend.management.service.StudentService;

import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;


@RestController 
@RequestMapping ("/api/students")
@AllArgsConstructor 
public class StudentController {
    StudentService service;

    @PostMapping("/")
    public ResponseEntity<Student> AddStudent(@RequestBody Student student) {
        Student savedStudent = service.saveOrUpdate(student);
        
        return new ResponseEntity<Student>(savedStudent, HttpStatus.CREATED);
    }

    @GetMapping("/")
    public ResponseEntity<List<Student>> getAllStudents() {
        List<Student> students = service.findAllStudents();
        return new ResponseEntity<List<Student>>(students, HttpStatus.ACCEPTED);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<Student> getStudentById(@PathVariable int id) {
        Student student = service.findStudentById(id);
        if (student == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(student, HttpStatus.ACCEPTED);
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<Student> updateStudent(@PathVariable int id, @RequestBody Student student) {
        Student existedStudent = service.findStudentById(id);

        if (existedStudent == null) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        
        return new ResponseEntity<>(service.saveOrUpdate(student), HttpStatus.ACCEPTED);
    }

    @DeleteMapping ("/{id}")
    public ResponseEntity<Void> deleteStudent(@PathVariable int id) {
        service.deleteStudentById(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @GetMapping("/courses/{course}")
    public ResponseEntity<List<Student>> getStudentsByCourse(@PathVariable String course) {
        return new ResponseEntity<>(service.findStudentsByCourse(course), HttpStatus.ACCEPTED);
    }
    
}
