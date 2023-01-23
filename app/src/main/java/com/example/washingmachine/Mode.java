package com.example.washingmachine;

public class Mode {

    //Entitäten eines Waschgangs
    private String key;
    private String name;
    private int runtime;

    //Klasse bildet eine Konstruktionsvorschrift für die objektorientierte Verwaltung der Waschgänge in der App
    public Mode(String key, String name, int runtime){
        this.key = key;
        this.name = name;
        this.runtime = runtime;
    }

    //Setter & Getter
    public String getKey() {
        return key;
    }

    public String getName() {
        return name;
    }

    public int getRuntime() {
        return runtime;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setRuntime(int runtime) {
        this.runtime = runtime;
    }
}
