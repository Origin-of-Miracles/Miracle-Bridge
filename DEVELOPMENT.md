# Miracle Bridge Development Environment

## Setup Status

✅ Project structure created
✅ Gradle build files configured
✅ Core Java classes implemented:
  - MiracleBridge (Main mod class)
  - MiracleBrowser (MCEF wrapper)
  - BrowserManager
  - BridgeAPI (JS ↔ Java communication)
  - IEntityDriver interface
  - YSMCompat & YSMEntityDriver

## Next Steps

### 1. Setup Development Environment

```bash
# Download Gradle wrapper (if not present)
gradle wrapper --gradle-version 8.5

# Setup workspace
./gradlew setupDecompWorkspace

# Generate IDE project files
./gradlew genIntellijRuns  # For IntelliJ IDEA
# OR
./gradlew eclipse          # For Eclipse
```

### 2. Install JDK 17

Ensure Java 17 is installed:
```bash
java -version  # Should show version 17.x.x
```

Recommended: [Microsoft Build of OpenJDK 17](https://docs.microsoft.com/en-us/java/openjdk/download#openjdk-17)

### 3. Build the Mod

```bash
# Build JAR file
./gradlew build

# Output: build/libs/miraclebridge-0.1.0-alpha.jar
```

### 4. Run Development Client

```bash
# Launch Minecraft with mod loaded
./gradlew runClient
```

**First Launch Notes:**
- MCEF will download CEF binaries (~200MB) on first run
- Requires internet connection for initial setup
- Downloads from: https://mcef-download.cinemamod.com

### 5. Platform-Specific Notes

#### macOS Users ⚠️
- MCEF: ✅ Supported (Chromium will work)
- YSM: ❌ NOT SUPPORTED (C++ native libraries incompatible)
- YSM features will be disabled automatically

#### Windows/Linux
- Full feature support

## Project Structure

```
Miracle-Bridge/
├── src/main/java/
│   └── com/originofmiracles/miraclebridge/
│       ├── MiracleBridge.java           # Main mod class
│       ├── browser/
│       │   ├── MiracleBrowser.java      # MCEF wrapper
│       │   └── BrowserManager.java      # Browser lifecycle
│       ├── bridge/
│       │   └── BridgeAPI.java           # JS ↔ Java bridge
│       └── entity/
│           ├── IEntityDriver.java       # Entity control interface
│           └── ysm/
│               ├── YSMCompat.java       # YSM command wrapper
│               └── YSMEntityDriver.java # YSM implementation
├── src/main/resources/
│   ├── META-INF/mods.toml              # Mod metadata
│   └── miraclebridge.mixins.json       # Mixin config
├── build.gradle                         # Build configuration
└── gradle.properties                    # Gradle settings
```

## Dependencies

All dependencies are automatically resolved by Gradle:

- **Forge**: 1.20.1-47.2.0
- **MCEF**: 2.1.6-1.20.1 (Chromium 116.0.5845.190)
- **Gson**: 2.10.1

## Development Roadmap

### Phase 1: Core Infrastructure (Current)
- [x] Project setup
- [x] MCEF integration
- [x] Basic bridge API
- [ ] Resource handler (serve React from jar)
- [ ] Network packet system

### Phase 2: Entity AI
- [x] IEntityDriver interface
- [x] YSM compatibility layer
- [ ] Pathfinding system
- [ ] Perception API (environment scanning)

### Phase 3: Advanced Features
- [ ] TTS audio streaming
- [ ] Dynamic BGM system
- [ ] LLM backend integration

## Testing

### Manual Testing
1. Run `./gradlew runClient`
2. Create new world or join server
3. Open browser overlay (keybind TBD)
4. Test JS API via browser console:
```javascript
// Test bridge API
fetch('bridge://api/getPlayerInfo', {
  method: 'POST',
  body: JSON.stringify({})
}).then(r => r.json()).then(console.log);
```

### Unit Tests
```bash
./gradlew test
```

## Troubleshooting

### "MCEF not initialized"
- Ensure internet connection on first launch
- Check logs for download errors
- Manually download from: https://mcef-download.cinemamod.com

### "YSM not found"
- YSM is optional dependency
- Features will gracefully degrade
- macOS: This is expected (not supported)

### Build Errors
```bash
# Clean and rebuild
./gradlew clean build --refresh-dependencies
```

## Contributing

See main project documentation for contribution guidelines.

## License

MIT License - See [LICENSE](LICENSE)

---

*Origin of Miracles Dev Team*
*Last Updated: 2025-12-16*
