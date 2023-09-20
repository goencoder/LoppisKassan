package se.teddy.loppiskassan.logging;

import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LambdaLogger extends Logger {
    public LambdaLogger(String name) {
        super(name, null);
    }

    public void fine(Callable<String> message) {
        // log only, if it's loggable
        if (isLoggable(Level.FINE)) {
            try {
                // evaluate here the callable method
                super.fine(message.call());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}