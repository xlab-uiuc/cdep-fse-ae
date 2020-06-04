FROM openjdk:8-jdk-alpine

#setup maven and env in docker

RUN apk add --no-cache curl tar bash python
ARG MAVEN_VERSION=3.6.3
ARG USER_HOME_DIR="/root"
RUN mkdir -p /usr/share/maven && \
curl -fsSL http://apache.osuosl.org/maven/maven-3/$MAVEN_VERSION/binaries/apache-maven-$MAVEN_VERSION-bin.tar.gz | tar -xzC /usr/share/maven --strip-components=1 && \
ln -s /usr/share/maven/bin/mvn /usr/bin/mvn
ENV MAVEN_HOME /usr/share/maven
ENV MAVEN_CONFIG "$USER_HOME_DIR/.m2"


#add source codes
RUN mkdir /cDep
RUN mkdir /result
WORKDIR /cDep/
COPY pom.xml /cDep/
COPY config_files /cDep/config_files
COPY app /cDep/app
COPY src /cDep/src
COPY parser.py /cDep/parser.py
RUN mvn -B  -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn compile
COPY run.sh /cDep/

ENV TARGET "0"
CMD ["sh","-c","/cDep/run.sh -a ${TARGET}"]
