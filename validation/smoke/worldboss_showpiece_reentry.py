"""Post-timing, process-owned TEST-only interruption of native showpiece publication."""
import argparse,json,time
from pathlib import Path
from rcon import Rcon
from worldboss_rehearsal import load,verify_owner,write_json

def run(path):
    rig,manifest,props=load(path);verify_owner(manifest)
    report={"scope":"Post-timing native TEST showpiece callback interruption; not capacity evidence","checks":[],"result":"FAIL"}
    def check(name,passed):
        report["checks"].append({"name":name,"passed":passed})
        if not passed: raise RuntimeError(name)
    try:
        with Rcon("127.0.0.1",manifest["rcon_port"],props["rcon.password"]) as client:
            check("private server is empty","There are 0 " in client.command("list"))
            check("previous event is TEST","TEST encounter" in client.command("cr worldboss status"))
            report["stop_previous"]=client.command("cr worldboss stop")
            check("previous TEST event stopped","No World Boss is active." in client.command("cr worldboss status"))
            report["armed"]=client.command("wbperf arm-showpiece-stop")
            check("post-timing re-entry fixture armed","WBPERF ARMED" in report["armed"])
            report["start"]=client.command("cr worldboss test start")
            check("new TEST preparation accepted","TEST preparation queued" in report["start"])
            deadline=time.monotonic()+30
            while time.monotonic()<deadline:
                response=client.command("wbperf showpiece-stop-result")
                if "WBPERF showpiece " not in response: raise RuntimeError("Missing fixture result")
                result=json.loads(response.split("WBPERF showpiece ",1)[1]); report["native_result"]=result
                if not result["armed"] and not result["entityPresent"]: break
                time.sleep(.2)
            check("native entity-load callback stopped the event",result["stopped"] and result["entity"] is not None and result["error"] is None)
            check("interrupted native actor was discarded",not result["entityPresent"])
            check("stale actor was never retained as the showpiece",result["tracked"] is None)
            check("interrupted event remains inactive",not result["active"])
            report["result"]="PASS"
    finally:
        write_json(rig/"logs/showpiece-reentry.json",report)
        print(json.dumps({"result":report["result"],"checks":len(report["checks"]),"passed":sum(c["passed"] for c in report["checks"])}))

if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument("--rig",type=Path,required=True)
    run(parser.parse_args().rig)
