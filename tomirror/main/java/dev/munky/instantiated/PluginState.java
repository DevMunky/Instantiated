package dev.munky.instantiated;

// i dont know why i made this
public enum PluginState {
      UNDEFINED,
      LOADING,
      ENABLING,
      RELOADING,
      PROCESSING,
      DISABLING,
      DISABLED;
      public boolean isStartup() {
            return this == LOADING || this == ENABLING;
      }
      
      public boolean isDisabled() {
            return this == DISABLING || this == DISABLED;
      }
      
      public boolean isSafe() {
            return this == PROCESSING;
      }
      
      public boolean isState(PluginState state) {
            return InstantiatedAPI.INSTANCE.getState() == state;
      }
}
