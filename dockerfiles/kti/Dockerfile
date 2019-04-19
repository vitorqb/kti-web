FROM openjdk:8-alpine

# Args
ARG KTI_VERSION

# Dependencies
RUN apk update && apk add curl bash

# Workdir
RUN mkdir /app
WORKDIR /app

# Prepares logging and config
COPY logging.xml logging.xml
COPY config.edn config.edn

# Downloads the kti bin
COPY install-kti.bash install-kti.bash
RUN chmod +x ./install-kti.bash\
        && ./install-kti.bash "$KTI_VERSION"\
        && rm -rf install-kti.bash

# Prepares entry script
COPY run-kti.bash run-kti.bash
RUN chmod +x run-kti.bash

# Exposes port
EXPOSE 3333

CMD ["/app/run-kti.bash"]
