package com.examples.android.healthcare.tasks;

public interface RepositoryCallback<T> {
    void onComplete(Result<T> result);
}
