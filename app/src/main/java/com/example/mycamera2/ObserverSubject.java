package com.example.mycamera2;

public interface ObserverSubject<T> {
    void registerObserver(T observer);
    void removeObserver(T observer);
}
