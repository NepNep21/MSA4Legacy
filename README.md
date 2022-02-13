# MSA4Legacy
Patches for the old minecraft official launcher to add microsoft account support  
My code here is quite atrocious in some parts, but it works so i decided to release it now, i will likely be improving it in the future  

# Requirements  

These patches bump the java version requirement from java 6 + jfx to java 8 + jfx, the java version i use for development can be found [here](https://cdn.azul.com/zulu/bin/zulu8.60.0.21-ca-fx-jdk8.0.322-linux_x64.tar.gz)  

# Usage  
1. Download the installer and patches from the Actions tab in this repository (requires an account)
2. Put the patches file in the same directory as the official launcher jar  
3. Run the installer and select the official launcher jar  
4. The patched jar will be in that same directory, but called "launcher-patched.jar"

# Development  
This project requires jdk8 due to a library, however to minimize binpatches any development must be done targeting java 6 bytecode, so java 6 syntax must be used  

To set up a decompiled environment, run the filterDecomp task  
To create patches with your changes, run the genSourceDiffs task  
To create a recompiled jar, run the jarLauncher task (DOES NOT KEEP UNSAVED PATCHES)  
To generate binary patches to use with the installer, run the genBinPatches task  

More tasks can be found in the build.gradle.kts file, please read which tasks depend on others to prevent unsaved work being lost  

# Is this legal?  
Yes, special care has been taken to keep this project 100% legal, by using both binary and source patches, no substantial mojang code is distributed by this project

I AM NOT AFFILIATED WITH NOR ENDORSED BY Microsoft Corporation NOR ITS SUBSIDIARY Mojang Studios IN ANY WAY  
