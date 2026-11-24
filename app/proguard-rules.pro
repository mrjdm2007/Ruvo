# Room entities and generated database implementations are kept through generated adapters.
# Add narrowly scoped keep rules only if release testing exposes reflection-based requirements.

# Room locates the generated database implementation by its class name at runtime.
-keep class com.ruvo.data.RuvoDatabase_Impl { *; }
-keep class **_Impl { *; }
