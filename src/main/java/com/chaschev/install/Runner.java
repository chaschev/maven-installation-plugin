package com.chaschev.install;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public class Runner {
    public static void main(String[] args) throws IOException {
        if(args.length < 2){
//            System.out.println(Arrays.asList(args));
            if(args.length == 1 && "SMOKE_TEST_HUH".equals(args[0])){
//                System.out.println("smoke test ok");
                return;
            }
            System.out.println("Runner <classpathFile> <class> [arguments]");
            System.exit(-1);
        }

        File classpathFilePath = new File(args[0]);
        String className = args[1];

        List<URL> classpathEntries = new ArrayList<URL>();
        BufferedReader reader = new BufferedReader(new FileReader(classpathFilePath));

        String line;

        String userHome = System.getProperty("user.home");

        while( (line = reader.readLine()) != null){
            line = line.replace("$HOME", userHome);
            classpathEntries.add(new File(line).toURI().toURL());
        }

        args = Arrays.copyOfRange(args, 2, args.length);

        new ExecObject.ClassRunner(
            className, classpathEntries,
            "",
            args,
            true,
            System.getProperties(),
            15000,
            true,
            null,
            null
        ).invoke();
    }
}
