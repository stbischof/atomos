package org.atomos.service.jfxapp;

import java.util.Hashtable;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
public class HelloFX extends Application
{


    @Override
    public void start(Stage stage)
    {

        final BundleContext bc = FrameworkUtil.getBundle(
            this.getClass()).getBundleContext();

        bc.registerService(Application.class, this, new Hashtable<String, Object>());

        final String javaVersion = System.getProperty("java.version");
        final String javafxVersion = System.getProperty("javafx.version");
        final Label l = new Label(
            "Hello, JavaFX " + javafxVersion + ", running on Java " + javaVersion + ".");
        final Scene scene = new Scene(new StackPane(l), 640, 480);
        stage.setScene(scene);
        stage.show();
    }


}