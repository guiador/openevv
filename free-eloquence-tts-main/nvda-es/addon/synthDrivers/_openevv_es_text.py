"""Opciones de lectura del port Android; independientes de NVDA y del audio."""
import re

MARKS = "-,.:;?!–—"
DEFAULTS = dict(pauses=2, breakPercent=100, phrasePrediction=False, forceLanguage=False,
                forceRate=False, forcePitch=False, forceVolume=False, numberMode=0,
                otp=False, stripSsml=False, spellingMode=0, capitalMode=0, textFixes=True,
                codeMode=False, camel=True, snake=True, operators=True, braces=True,
                indentation=True, paths=True, urls=True)
NATO = "Alpha Bravo Charlie Delta Echo Foxtrot Golf Hotel India Juliett Kilo Lima Mike November Oscar Papa Quebec Romeo Sierra Tango Uniform Victor Whiskey X-ray Yankee Zulu".split()
NATO_DIGITS = "Zero One Two Three Four Five Six Seven Eight Nine".split()
OPERATORS = {
    ">>>":"unsigned right shift", "...":"dot dot dot", "!==":"bang double equals",
    "===":"triple equals", "<<":"left shift", ">>":"right shift", "==":"double equals",
    "!=":"not equals", "<=":"less than or equal to", ">=":"greater than or equal to",
    "++":"plus plus", "--":"minus minus", "+=":"plus equals", "-=":"minus equals",
    "*=":"star equals", "/=":"slash equals", "%=":"percent equals", "&=":"and equals",
    "|=":"or equals", "^=":"xor equals", "&&":"and and", "||":"or or", "!!":"bang bang",
    "??":"double question", "?.":"question dot", "->":"arrow", "=>":"fat arrow",
    "<-":"left arrow", "::":"double colon", "..":"dot dot", "=":"equals", "+":"plus",
    "-":"minus", "*":"star", "/":"slash", "%":"percent", "&":"ampersand", "|":"bar",
    "^":"caret", "~":"tilde", "!":"bang", "?":"question", "<":"less than", ">":"greater than",
}
BRACES = dict(zip("{}[]();:,.#@$", ("left brace", "right brace", "left bracket", "right bracket",
    "left paren", "right paren", "semicolon", "colon", "comma", "dot", "hash", "at", "dollar")))

def outside_tags(text, transform):
    """Conserva anotaciones del motor: sus números nunca son texto ordinario."""
    return "".join(part if part.startswith("`") else transform(part)
                   for part in re.split(r"(`[^\s]*)", text))

def shorten_pauses(text, mode, last=False):
    if not mode or not text:
        return text
    if mode == 2:
        def punctuation(part):
            out=[]; i=0
            while i<len(part):
                c=part[i]; j=i+1
                if c in MARKS:
                    while j<len(part) and part[j]==c: j+=1
                    prev=i>0 and (part[i-1].isalnum() or part[i-1].isspace())
                    following=j==len(part) or part[j].isspace() or part[j] in "/\\"
                    if prev and following:
                        out.append(" `p1"+part[i:j]); i=j; continue
                out.append(c); i+=1
            return "".join(out)
        text=outside_tags(text,punctuation)
    if last and text.rstrip()[-1:] not in MARKS:
        text += " `p100"
    return text

def numbers(text, mode):
    if mode not in (1,2,3): return text
    def transform(part):
        out=[]; i=0
        while i<len(part):
            if part[i]=="+" and i+1<len(part) and part[i+1] in "0123456789":
                out.append("+ "); i+=1; continue
            if part[i] not in "0123456789": out.append(part[i]); i+=1; continue
            j=i
            while j<len(part) and part[j] in "0123456789": j+=1
            k=j
            while k<len(part) and part[k] in ",.":
                end=k+1
                while end<len(part) and part[end] in "0123456789": end+=1
                if end-k-1!=3: break
                k=end
            if k>j: out.append(part[i:k]); i=k; continue
            decimal=(i>=2 and part[i-1] in ",." and part[i-2].isdigit()) or (j+1<len(part) and part[j] in ",." and part[j+1].isdigit())
            run=part[i:j]
            if len(run)<6 or decimal: out.append(run)
            else:
                size={1:1,2:2,3:3}[mode]; groups=[]
                for at in range(0,len(run),size):
                    group=run[at:at+size]
                    groups.append(" ".join(group) if len(group)>1 and group[0]=="0" else group)
                out.append(" ".join(groups))
            i=j
        return "".join(out)
    return outside_tags(text,transform)

def otp(text):
    def transform(part):
        def found(match):
            raw=match.group(); groups=re.split(r"[ \-‑–]",raw); digits="".join(groups)
            if not 4<=len(digits)<=8 or len({len(g) for g in groups})>1: return raw
            before=part[max(0,match.start()-80):match.start()].lower()
            after=part[match.end():match.end()+80].lower()
            context=before+" "+after
            specific=("otp","one-time","verification","passcode","security code","login code",
                      "two-factor","pin","código","codigo","verificación","verificacion",
                      "sicherheitscode","bestätigungscode","verification","vérification","codice","token")
            marked=any(term in context for term in specific) or re.search(r"\bcode\b",context)
            negative=any(term in before[-40:] for term in ("order","invoice","account","amount","flight","pedido","factura","cuenta","commande","fattura","rechnung"))
            adjacent=part[max(0,match.start()-1):match.start()]+part[match.end():match.end()+1]
            if negative or any(c in "$€£%:.,/" for c in adjacent): return raw
            if not marked and part.strip()!=raw: return raw
            return ", ".join(" ".join(g) for g in groups)
        return re.sub(r"(?<![\w])\d+(?:[ \-‑–]\d+){0,2}(?![\w])",found,part)
    return outside_tags(text,transform)

def spelling(text, mode):
    if mode not in (1,2,3,4): return text
    def word(match):
        token=match.group()
        if mode in (3,4) and (len(token)<3 or not token.isupper()): return token
        if mode in (2,4):
            return " ".join(NATO[ord(c.lower())-97] if c.isascii() and c.isalpha()
                else NATO_DIGITS[int(c)] if c in "0123456789" else c for c in token)
        return "`ts1 "+" ".join(token)+" `ts0 "
    return outside_tags(text,lambda part:re.sub(r"[^\W_]+",word,part))

def code(text, options):
    if not options.get("codeMode"): return text
    def transform(part):
        # Match paths before operators; longest operator always wins.
        def path_names(match):
            names={"/":"slash","\\":"backslash",".":"dot",":":"colon","_":"underscore","-":"dash","@":"at"}
            return re.sub(r"[/\\.:_@-]",lambda m:" "+names[m.group()]+" ",match.group())
        if options.get("urls"):
            part=re.sub(r"(?:https?://|www\.)[^\s]+",path_names,part)
        if options.get("paths"):
            part=re.sub(r"(?:[A-Za-z]:[\\/]|/|\.\.?/)[^\s]+",path_names,part)
        if options.get("camel"):
            part=re.sub(r"([a-z0-9])([A-Z])",r"\1 \2",part)
            part=re.sub(r"([A-Z])([A-Z][a-z])",r"\1 \2",part)
        if options.get("snake"):
            part=re.sub(r"(?<=\w)_(?=\w)"," ",part)
        names={}
        if options.get("operators"): names.update(OPERATORS)
        if options.get("braces"): names.update(BRACES)
        if names:
            pattern="|".join(re.escape(k) for k in sorted(names,key=len,reverse=True))
            part=re.sub(pattern,lambda m:" "+names[m.group()]+" ",part)
        if options.get("indentation"):
            part=re.sub(r"(?m)^[ \t]+",lambda m:"indentation %d " % sum(4 if c=="\t" else 1 for c in m.group()),part)
        return part
    return outside_tags(text,transform)

def apply(text, options, character_mode=False):
    if not text: return text
    if options.get("stripSsml"):
        text=re.sub(r"</?[A-Za-z][^<>]*>"," ",text)
    text=code(text,options)
    if options.get("textFixes"):
        def fixes(part):
            part=part.translate(dict.fromkeys(map(ord,"\u200b\u200c\u200d\u2060\ufeff\u00ad\ufffc\u180e")))
            part=''.join(' ' if c.isspace() else c for c in part)
            part=re.sub(r"([^\W\d_])([0-9])",r"\1 \2",part)
            part=re.sub(r'([^\W\d_])([~#$%^*({|\[<"•])',r'\1 \2',part)
            part=re.sub(r'([^\W\d_])\s+\(s\)',r'\1(s)',part)
            part=re.sub(r"\s+([.,:;?!])(?![^\W_])",r"\1",part)
            part=re.sub(r' +',' ',part)
            return part
        text=outside_tags(text,fixes)
    if options.get("otp"): text=otp(text)
    text=numbers(text,options.get("numberMode",0))
    if not character_mode:
        mode=options.get("capitalMode",0)
        if mode:
            def capitals(part):
                return re.sub(r"(?<![\w'])[A-Z](?![\w'])",lambda m:
                    " cap "+m.group() if mode==1 else " `vbst+6 "+m.group()+" `vbst-6 ",part)
            text=outside_tags(text,capitals)
        text=spelling(text,options.get("spellingMode",0))
    return text
