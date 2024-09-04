package com.showcase.core.beans.photoshop;

public class Layer{
    public String name;
    public int index;
    public Input input;
    public Edit edit;
    public SmartObject smartObject;
    public Text text;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Input getInput() {
        return input;
    }

    public void setInput(Input input) {
        this.input = input;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public Edit getEdit() {
        return edit;
    }

    public void setEdit(Edit edit) {
        this.edit = edit;
    }

    public SmartObject getSmartObject() {
        return smartObject;
    }

    public void setSmartObject(SmartObject smartObject) {
        this.smartObject = smartObject;
    }

    public Text getText() {
        return text;
    }

    public void setText(Text text) {
        this.text = text;
    }
}