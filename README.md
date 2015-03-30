# toxiclibs
[Toxiclibs][] is a very impressive set of libraries for use with processing (java / jruby / whatever) created by the toxmeister Karsten Schmidt (aka [postspectacular][]). Once they were available or at least linked to by [processing.org][], but since recent changes they have become more obscure which is shame 'cos they are brilliant (still work, and still relevant)...

This is a super convenient github fork of the official toxiclibs repo, for my ruby-processing / JRubyArt development work.  Now it will be absolutely clear where credit / blame lies for any changes (to the toxi original approximating to toxiclibs-21)....

Refactored/reworked for jdk7+ and processing-2.2.1 (in Netbeans)....

Can be build with ant and jdk7+:-
```bash
cd toxiclibs/ant
ant -f build-all.xml
```
Unzip toxiclibs-complete-***.zip in your sketchbook/libraries folder (or wherever else you put your libraries) to use with vanilla processing

See also [toxigem][] for the ruby gem built using the source developed here, that can be used by both ruby-processing and JRubyArt (please let me know if you can use it elsewhere).
```bash
gem install toxiclibs
```

[postspectacular]:http://postspectacular.com/
[Toxiclibs]:http://toxiclibs.org/
[processing.org]:https://processing.org/
[toxigem]:https://github.com/ruby-processing/toxicgem
