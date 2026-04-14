FROM ubuntu:22.04

# Seteaza argumentele ca sa nu ceara interactiune
ENV DEBIAN_FRONTEND=noninteractive

# Instaleaza setul esential de scule inclusiv Java 17 pentru Android
RUN apt-get update && apt-get install -y \
    openjdk-17-jdk \
    wget \
    unzip \
    git \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Setari environment pentru Android SDK
ENV ANDROID_HOME=/opt/android-sdk
ENV PATH=${PATH}:${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools
ENV cmdline_tools_url=https://dl.google.com/android/repository/commandlinetools-linux-9477386_latest.zip

# Descarca si configureaza Command Line Tools pentru Android
RUN mkdir -p ${ANDROID_HOME}/cmdline-tools && \
    wget -q ${cmdline_tools_url} -O cmdline-tools.zip && \
    unzip -q cmdline-tools.zip -d ${ANDROID_HOME}/cmdline-tools/ && \
    mv ${ANDROID_HOME}/cmdline-tools/cmdline-tools ${ANDROID_HOME}/cmdline-tools/latest && \
    rm cmdline-tools.zip

# Accepta licentele si instaleaza platforma necesara
RUN yes | sdkmanager --licenses && \
    sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"

# Seteaza directorul de munca
WORKDIR /app

# Copiaza tot proiectul Android in container
COPY . .

# Permisiuni de executie pentru gradle
RUN chmod +x gradlew

# Precompilare
# Daca vrem sa generam fisierul .apk la start
CMD ["./gradlew", "assembleDebug", "--no-daemon"]
