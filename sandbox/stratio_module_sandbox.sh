
STRATIO_ENV=$1
STRATIO_MODULE_VERSION=$2


#######################################
## SERVICES
#######################################

echo -e '\e[1;34mInstalling services...'

apt-get install apache2 stratio-streaming stratio-streaming-shell -q -y --force-yes
update-rc.d streaming defaults


service kafka start
service zookeeper start
service cassandra start
service elasticsearch start
service mongod start
service streaming start


#######################################
## KIBANA
#######################################


if [ ! -d "/var/www/html/kibana" ]; then
    echo -e '\e[1;34mDownloading kibana...'
	wget  'https://download.elasticsearch.org/kibana/kibana/kibana-3.1.0.tar.gz' -P /home/vagrant/downloads
	
	echo -e '\e[1;34mUncompressing kibana...'
	tar -xzf /home/vagrant/downloads/kibana-3.1.0.tar.gz -C /var/www/html
	mv /var/www/html/kibana-3.1.0 /var/www/html/kibana
fi


####################
## STRATIO STREAMING EXAMPLES
####################

DOWNLOAD_EXAMPLES_URL="https://s3.amazonaws.com/stratioorg/streaming-examples-${STRATIO_MODULE_VERSION}-app.tar.gz"


if [ ! -d "/opt/sds/streaming-examples" ]; then

	echo -e '\e[1;34mBuilding Stratio Streaming Examples...'
	mkdir /opt/sds/streaming-examples	
	wget $DOWNLOAD_EXAMPLES_URL -P /home/vagrant/downloads
	tar -xzf /home/vagrant/downloads/streaming-examples-$STRATIO_MODULE_VERSION-app.tar.gz -C /home/vagrant/downloads
	cp -fr /home/vagrant/downloads/streaming-examples-$STRATIO_MODULE_VERSION/* /opt/sds/streaming-examples
fi




###################
## KIBANA DASHBOARDS
###################
cp -f /opt/sds/streaming-examples/dashboards/*.json /var/www/html/kibana/app/dashboards
chmod -R 777 /var/www/html/kibana/app/dashboards


echo -e '\e[1;34m    _____ __             __  _          _____ __                            _             '
echo -e '\e[1;34m   / ___// /__________ _/ /_(_)___     / ___// /_________  ____ _____ ___  (_)___  ____ _ '
echo -e '\e[1;34m   \\__ \\/ __/ ___/ __ `/ __/ / __ \\    \\__ \\/ __/ ___/ _ \\/ __ `/ __ `__ \\/ / __ \\/ __ `/ '
echo -e '\e[1;34m  ___/ / /_/ /  / /_/ / /_/ / /_/ /   ___/ / /_/ /  /  __/ /_/ / / / / / / / / / / /_/ /  '
echo -e '\e[1;34m /____/\\__/_/   \\__,_/\\__/_/\\____/   /____/\\__/_/   \\___/\\__,_/_/ /_/ /_/_/_/ /_/\\__, /   '
echo -e '\e[1;34m                                                                                /____/    '
echo -e ''
echo -e ''
echo -e '\e[1;33m--------------------'
echo -e '\e[1;33mCongrat, your Stratio Streaming Sandbox is ready'
echo -e '\e[1;33m--------------------'
echo -e '\e[1;33mDocumentation available at:'
echo -e '\e[1;33mhttp://www.openstratio.com'
echo -e '\e[1;33m--------------------'
echo -e '\e[1;33mDefault ip: 10.10.10.10'
echo -e '\e[1;33mDefault hostname: stratio-streaming'
echo -e '\e[1;33mKibana url: http://10.10.10.10/kibana'
echo -e '\e[1;33mKafka port: 9092'
echo -e '\e[1;33mZookeeper port: 2181'
echo -e '\e[1;33m--------------------'
echo -e '\e[1;33mContact us if you need support at contact@stratio.com'
echo -e '\e[1;33m--------------------'
echo -e '\e[1;33mType vagrant ssh to enter the Stratio Streaming sandbox.'
