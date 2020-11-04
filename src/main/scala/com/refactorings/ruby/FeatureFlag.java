package com.refactorings.ruby;

public enum FeatureFlag {
  ExampleFeature;

  private boolean currentValue = false;

  public void activateIn(Runnable closure) {
    boolean oldValue = currentValue;
    currentValue = true;
    closure.run();
    currentValue = oldValue;
  }

  public boolean isActive() {
    return currentValue;
  }
}
