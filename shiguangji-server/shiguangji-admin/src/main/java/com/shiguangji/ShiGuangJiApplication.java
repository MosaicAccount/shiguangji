package com.shiguangji;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;

/**
 * 启动程序
 * 
 * @author shiguangji
 */
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
public class ShiGuangJiApplication
{
    public static void main(String[] args)
    {
        // System.setProperty("spring.devtools.restart.enabled", "false");
        SpringApplication.run(ShiGuangJiApplication.class, args);
        System.out.println("""
            
                        (♥◠‿◠)ﾉﾞ  拾光记启动成功   ლ(´ڡ`ლ)ﾞ
                        
      _____   _       _    _____                                        _   _ 
     / ____| | |     (_)  / ____|                                      | | (_)
    | (___   | |__    _  | |  __   _   _    __ _   _ __     __ _       | |  _ 
     \\___ \\  | '_ \\  | | | | |_ | | | | |  / _` | | '_ \\   / _` |  _   | | | |
     ____) | | | | | | | | |__| | | |_| | | (_| | | | | | | (_| | | |__| | | |
    |_____/  |_| |_| |_|  \\_____|  \\__,_|  \\__,_| |_| |_|  \\__, |  \\____/  |_|
                                                             __/ |             
                                                            |___/              
        """);
    }
}
