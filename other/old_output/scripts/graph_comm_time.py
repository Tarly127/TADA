from dateutil.parser import *
from datetime import *

LINE_SEPARATOR   = ";"
LINE_TOKEN_COUNT = 6

class Key:
    round: int
    sender_port: int
    receiver_port: int
    reqID_internalID: int

    def __init__(self, round: int, sender_port: int, reciever_port: int, reqID_interalID: int):
        self.round = round
        self.sender_port = sender_port
        self.receiver_port = reciever_port
        self.reqID_internalID = reqID_interalID

    def __eq__(self, __o: object) -> bool:

        if __o is not None and isinstance(__o, Key):
            return self.reqID_internalID == __o.reqID_internalID and self.round == __o.round and self.sender_port == __o.sender_port and self.receiver_port == __o.receiver_port

        return False


class Metrics:
    key: Key
    starting_time: datetime
    elapsed_time: int
    message_type: str
    
    # parses line
    def __init__(self, name: str, line: str, sender: bool) -> None:

        # type;senderPort;reqIdInternal;Round;startWalltime;duration

        line_tokens = line.split(LINE_SEPARATOR)

        if len(line_tokens) == LINE_TOKEN_COUNT:

            self.type          = line_tokens[0]
            self.starting_time = parse(line_tokens[4])
            self.elapsed_time  = int(line_tokens[5])

            if sender:
                other_port     = int(line_tokens[1])
                reqId_internal = int(line_tokens[2])
                round          = int(line_tokens[3])

                self.key = Key(round, 
                                sender_port     = name if sender else other_port, 
                                reciever_port   = name if not sender else other_port, 
                                reqID_interalID = reqId_internal)

            

        

        


def parse_csv(path: str) -> list[Metrics]:

    metrics = []

    with open(path, "r") as input_file:
        filename = path.split("/")[len(path.split("/")) - 1]

        port   = filename.split("_")[1]
        sender = "send" in filename

        first_line = True

        for line in input_file:
            if not first_line:
                metrics.append(Metrics(port, line, sender))
            else:
                first_line = False

    return metrics




