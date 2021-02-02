
const containerStyle = {
    position: 'fixed',
    top: 15,
    right: 22,
    zIndex: 9999
};
const visible = {
    visibility: 'visible',
    opacity: 1,
    transition: 'opacity 1s linear',
    boxShadow: 'rgb(102, 102, 102) 5px 5px 5px'
};
const hidden = {
    visibility: 'hidden',
    opacity: 0,
    transition: 'visibility 0s 1s, opacity 1s linear'
};
const diplayTime = 5000;

module.exports = function () {
    const Toast = React.createClass({

        render: function () {
            const { toastData, onClick } = this.props;
            const { id, message, type, isInHideTransition } = toastData;
            const toastCls = 'alert alert-' + type;
            const toastStyle = isInHideTransition ? hidden : visible;

            return (
                <div className={toastCls} style={toastStyle}>
                    <span style={{ position: 'relative', top: -10, right: -10, float: 'right', fontSize: '150%', cursor: 'pointer' }}
                        className="glyphicon glyphicon-remove-sign"
                        onClick={() => onClick(id)}
                    />
                    <span>{message}</span>
                </div>
            );
		}

    });
    
    return React.createClass({

        getInitialState: function () {
            return {
                toasters: []
            };
        },

        componentWillReceiveProps: function (newProps) {
            const toasters = newProps.toasters.reduce((acc, curr) => {
                const existingToaster = this.state.toasters.find(oldToaster => oldToaster.id === curr.id);

                if (existingToaster) {
                    acc.push(existingToaster);

                } else {
                    acc.push(Object.assign(curr, { isVisible: true, isInHideTransition: false }));
                    this.setDelayedHide(curr.id);
                }

                return acc;
            }, []);

            this.setState({ toasters });
        },

        setDelayedHide: function (id) {
            const self = this;
            setTimeout(() => self.beginHide(id), diplayTime);
        },

        beginHide: function (id) {
            const toasters = this.state.toasters.map(toaster => toaster.id === id ? Object.assign(toaster, { isInHideTransition: true }) : toaster);
            this.setState({ toasters });

            const self = this;
            // css transition is 1 second (see object 'hidden') so wait 1 second before removing alert from dom.
            setTimeout(() => self.endHide(id), 1000);
        },

        endHide: function (id) {
            const toasters = this.state.toasters.map(toaster =>
                toaster.id === id
                    ? Object.assign(toaster, { isVisible: false, isInHideTransition: false })
                    : toaster
            );
            this.setState({ toasters });
            this.props.removeToasterHandler(id);
        },

        render: function () {
            const toasters = this.state.toasters.filter(toaster => toaster.isVisible);

            if (toasters.length === 0)
                return null;

            return (
                <div style={containerStyle}>{
                    _.map(toasters, (toastData, idx) => <Toast key={idx} toastData={toastData} onClick={this.beginHide} />)
                }</div>
            );
        }
    });
};
