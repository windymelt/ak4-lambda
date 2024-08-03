//> using scala 3.3.1
//> using toolkit default

os.proc("sbt", "fullOptJS").call()

if (os.exists(os.pwd / "lambda.zip")) os.remove(os.pwd / "lambda.zip")

if (os.exists(os.pwd / "lambda")) os.remove.all(os.pwd / "lambda")

os.makeDir(os.pwd / "lambda")
os.copy(os.pwd / "target" / "scala-3.4.2" / "ak4-lambda-opt", os.pwd / "lambda")

os.proc("zip", "-r", "lambda.zip", "lambda").call()
