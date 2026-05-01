package com.company.workflowautomation.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Scanner;

public class PasswordHashedGenerator {

     public static void main(String[] args) {
         Scanner scanner = new Scanner(System.in);
         System.out.println("Enter Hash Password");
         String rawPass = scanner.nextLine();

         BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

         String hashed = encoder.encode(rawPass);
         System.out.println(hashed);
    }
}
