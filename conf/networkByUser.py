import requests
import json
from dse.cluster import Cluster, GraphExecutionProfile, EXEC_PROFILE_GRAPH_DEFAULT
from dse.cluster import GraphOptions
import sys
import logging
import datetime
from cassandra import ConsistencyLevel
from cassandra.policies import DCAwareRoundRobinPolicy
import random
from config import Config

log = logging.getLogger()
logfileHandler = logging.FileHandler('./githubapp.log')
log.addHandler(logfileHandler)
logging.basicConfig()
log.setLevel('INFO')

#
# -------------
# might switch tokens

proxies = {
    "http": None,
    "https": None,
}
tokens = []

def getToken():
    global tokens
    return tokens[random.randint(0, len(tokens) - 1)]

# simple helper methods to keep the code below cleaner
def followersURL(login):
    return 'https://api.github.com/users/' + login + '/followers?access_token=' + getToken()

def userURL(login):
    return str('https://api.github.com/users/' + login + '?access_token=' + getToken())


def userReposURL(login):
    return str('https://api.github.com/users/' + login + '/repos?access_token=' + getToken())

def getPageJSON(URL):
    page = requests.get(URL, proxies=proxies)
    page_text = page.text
    page_json = json.loads(page_text)
    return page_json


#
# simple class for connecting to C*, GitHub API and creating V/Es
#

class GitHubByUser:
    session = None

    def addAttendingEdges(self, login):
        result = self.session.execute_graph(
            'g.V().hasLabel("github_user").has("account", account).outE("attending")',
            {'account': login.lower()})

        if len(list(result)) == 0:
            self.session.execute_graph('v1 = g.V().hasLabel("github_user").has("account", sourceID).next()\n' +
                                       'v2 = g.V().hasLabel("cassandra_summit").'
                                       'has("name", "cassandra_summit").next()\n' +
                                       'v1.addEdge("attending", v2)',
                                       {"sourceID": login.lower()})

        result = self.session.execute_graph('g.V().hasLabel("github_user").'
                                            'has("account", account).outE("coding_language")',
                                            {'account': login.lower()})
        if len(list(result)) == 0:
            repoJSON = getPageJSON(userReposURL(login))
            languages = set()
            for i in range(0, len(repoJSON), 1):
                language = repoJSON[i]['language']
                if language:
                    languages.add(language)
            languagesList = list(languages)
            if len(languagesList) != 0:
                for i in range(0, len(languagesList), 1):
                    self.session.execute_graph('g.V().hasLabel("coding_language").has("name",lang)'
                                               '.tryNext().orElseGet {'
                                               'g.addV(label,"coding_language","name",lang)}',
                                                   {"lang": languagesList[i]})

                    self.session.execute_graph('v1 = g.V().hasLabel("coding_language").has("name",lang).next()\n' +
                                               'v2 = g.V().hasLabel("github_user").has("account", destinationID).next()\n' +
                                               'v2.addEdge("develops_in", v1)',
                                               {"lang": languagesList[i],
                                                "destinationID": login.lower()})


    def returnOrCreateAccount(self, login, attending):
        # check to see if user exists and create if needed
        account = getPageJSON(userURL(login))

        self.session.execute_graph('g.V().hasLabel("github_user").has("account", account)'
                                   '.tryNext().orElseGet {'
                                   'g.addV(label, "github_user", "account", account, "location", location,'
                                   '"dateJoined", created_at,'
                                   '"company", company).next()}',
                                   {'account': login.lower(),
                                    'location': account['location'],
                                    'created_at': account['created_at'],
                                    'company': account['company']})  # uses the default execution profile

        # if account (Vertex) exists but edge does not, create edge
        if attending:
            self.addAttendingEdges(login)

    def add_edge_following(self, sourceID, destinationID):
        # add 'following' edge between two github users

        self.session.execute_graph('g.V().hasLabel("github_user").has("account", account)'
                                   '.tryNext().orElseGet {'
                                   'g.addV(label, "github_user", "account", account).next()}',
                                   {'account': destinationID.lower()})

        try:

            result = self.session.execute_graph('v1 = g.V().hasLabel("github_user").has("account", sourceID).next()\n' +
                                                'v2 = g.V().hasLabel("github_user").has("account", destinationID).next()\n' +
                                                'v1.addEdge("following", v2)',
                                                {"sourceID": sourceID.lower(),
                                                 "destinationID": destinationID.lower()})

        except:
            log.info('account missing of following edge' + destinationID)
            return ""


        return result

    def connect(self, nodes):
        graph_name = 'cassandra_summit'
        local_datacenter = 'Cassandra + Graph'
        # local_datacenter = 'GraphAnalytics'
        ep = GraphExecutionProfile(graph_options=GraphOptions(graph_name=graph_name),
                                   load_balancing_policy=DCAwareRoundRobinPolicy(local_dc=local_datacenter),
                                   consistency_level=ConsistencyLevel.LOCAL_QUORUM)
        cluster = Cluster(nodes, execution_profiles={EXEC_PROFILE_GRAPH_DEFAULT: ep})
        metadata = cluster.metadata
        self.session = cluster.connect()
        log.info('Connected to cluster: ' + metadata.cluster_name)
        for host in metadata.all_hosts():
            log.info('Datacenter: %s; Host: %s; Rack: %s',
                     host.datacenter, host.address, host.rack)

    def close(self):
        self.session.cluster.shutdown()
        self.session.shutdown()
        log.info('Connection closed')
        log.info('Closed Timestamp: {:%Y-%m-%d %H:%M:%S}'.format(datetime.datetime.now()))


# wrapping it all up in main
# default profile for DSE Python driver is exec async
# default CL is local_quorum
#

def main():
    global tokens
    # --------------------
    log.info('Start Timestamp: {:%Y-%m-%d %H:%M:%S}'.format(datetime.datetime.now()))
    if len(sys.argv) != 3:
        print "Incorrect number of command line arguments, two expected " + str(len(sys.argv) - 1) + " provided"
        print "Expected format: networkByUser.py IPAddress accountName"
        log.error("Incorrect number of command line arguments, two expected " + str(len(sys.argv) - 1) + " provided")
        log.error('Expected format: networkByUser.py IPAddress accountName')
        exit()
    else:
        contactNode = sys.argv[1]
        githubAcct = str(sys.argv[2])

    f = file('application.cfg')
    cfg = Config(f)
    tokens = cfg.tokens

    # --------------------
    # connect to DSE Cluster and loop through GitHub API calls
    #
    client = GitHubByUser()
    client.connect([contactNode])

    # if needed create 'login' vertex and edge to Summit
    client.returnOrCreateAccount(githubAcct, True)

    # return accounts following the summit attendee account
    followers = getPageJSON(followersURL(githubAcct))

    # loop through followers
    for e in range(0, len(followers), 1):
        follower = followers[e]['login']
        client.returnOrCreateAccount(follower, False)
        client.add_edge_following(follower, githubAcct)

        # and users who are following the followers!
        l2_following = getPageJSON(followersURL(follower))
        for f in range(0, len(l2_following), 1):
            l2_follower = l2_following[f]['login']
            client.returnOrCreateAccount(l2_follower, False)
            client.add_edge_following(l2_follower, follower)

    client.close()

if __name__ == "__main__":
    main()
