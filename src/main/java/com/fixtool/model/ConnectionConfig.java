package com.fixtool.model;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Modelo reactivo de configuración de conexión con la BOSGAME.
 * Usa propiedades observables para que la UI refleje cambios automáticamente.
 *
 * SEGURIDAD: La contraseña NO tiene valor por defecto en código.
 * Se escribe únicamente en el diálogo de conexión y se borra al desconectar o salir.
 */
public class ConnectionConfig {

    // ── Valores por defecto (sin contraseña) ─────────────────────────────────
    public static final String DEFAULT_IP   = AppConfig.DEFAULT_IP;
    public static final String DEFAULT_USER = AppConfig.DEFAULT_USER;

    // ── Propiedades de conexión ───────────────────────────────────────────────
    private final StringProperty  ipTarget     = new SimpleStringProperty(DEFAULT_IP);
    private final StringProperty  username     = new SimpleStringProperty(DEFAULT_USER);
    private final StringProperty  password     = new SimpleStringProperty(""); // Empieza vacía
    private final StringProperty  sharedFolder = new SimpleStringProperty("");  // Carpeta compartida
    private final BooleanProperty connected    = new SimpleBooleanProperty(false);

    public ConnectionConfig() {}

    // ── IP Target ─────────────────────────────────────────────────────────────
    public String getIpTarget()               { return ipTarget.get();  }
    public void   setIpTarget(String val)     { ipTarget.set(val);      }
    public StringProperty ipTargetProperty()  { return ipTarget;        }

    // ── Usuario ───────────────────────────────────────────────────────────────
    public String getUsername()               { return username.get();  }
    public String getUserFast()               { return username.get();  }
    public void   setUsername(String val)     { username.set(val);      }
    public StringProperty usernameProperty()  { return username;        }

    // ── Contraseña (solo en memoria) ──────────────────────────────────────────
    public String getPassword()               { return password.get();  }
    public void   setPassword(String val)     { password.set(val);      }
    public StringProperty passwordProperty()  { return password;        }

    // ── Carpeta compartida ────────────────────────────────────────────────────
    public String getSharedFolder()              { return sharedFolder.get();  }
    public void   setSharedFolder(String val)    { sharedFolder.set(val);      }
    public StringProperty sharedFolderProperty() { return sharedFolder;        }

    // ── Estado de conexión ────────────────────────────────────────────────────
    public boolean isConnected()               { return connected.get();  }
    public void    setConnected(boolean val)   { connected.set(val);      }
    public BooleanProperty connectedProperty() { return connected;        }

    /**
     * Devuelve la ruta UNC de la carpeta compartida (\\IP\carpeta).
     * Usada por Puente de archivos y Estabilidad para acceder al recurso SMB.
     */
    public String getUncPath() {
        String folder = getSharedFolder().isBlank() ? "" : getSharedFolder();
        return "\\\\" + getIpTarget() + "\\" + folder;
    }

    /**
     * Restablece ip y usuario a valores por defecto y borra contraseña/conexión.
     * Llamar al desconectar o al salir de la app.
     */
    public void resetToDefaults() {
        ipTarget.set(DEFAULT_IP);
        username.set(DEFAULT_USER);
        clearPassword();
        sharedFolder.set("");
        connected.set(false);
    }

    /**
     * Borra la contraseña de la memoria.
     * Llamar siempre al desconectar o al cerrar la app.
     */
    public void clearPassword() {
        password.set("");
    }

    @Override
    public String toString() {
        return String.format("Target: %s | User: %s | Conectado: %s",
                getIpTarget(), getUsername(), isConnected() ? "Sí" : "No");
    }
}
